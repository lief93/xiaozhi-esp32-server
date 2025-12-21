import os
import re
import time
import wave
import shutil
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
from typing import Optional

import opuslib_next


@dataclass(frozen=True)
class RecordingConfig:
    enabled: bool = False
    root_dir: str = "/recordings"
    segment_seconds: int = 180
    sample_rate: int = 16000
    channels: int = 1
    mp3_bitrate: str = "64k"
    ffmpeg_path: str = "ffmpeg"
    keep_wav: bool = False


def sanitize_device_id(device_id: str) -> str:
    if not device_id:
        return "unknown"
    device_id = device_id.strip()
    safe = re.sub(r"[^a-zA-Z0-9._-]+", "_", device_id)
    return safe or "unknown"


class AudioRecorder:
    def __init__(self, config: RecordingConfig, device_id: str, session_id: str):
        self.config = config
        self.device_id = device_id
        self.device_dir = sanitize_device_id(device_id)
        self.session_id = session_id

        self._decoder: Optional[opuslib_next.Decoder] = None
        self._wav_fp: Optional[wave.Wave_write] = None
        self._wav_path: Optional[str] = None
        self._frames_written: int = 0
        self._segment_index: int = 0
        self._segment_start_epoch: Optional[int] = None

    @staticmethod
    def _time_range_label(start: datetime, end: datetime) -> str:
        # If it crosses day boundary, include date to avoid ambiguity.
        if start.date() != end.date():
            return f"{start.strftime('%Y%m%d%H%M%S')}-{end.strftime('%Y%m%d%H%M%S')}"
        return f"{start.strftime('%H%M%S')}-{end.strftime('%H%M%S')}"

    def append_audio_packet(self, packet: bytes, audio_format: str) -> None:
        if not self.config.enabled:
            return
        if not packet:
            return

        self._ensure_segment_open()
        if self._wav_fp is None:
            return

        if audio_format == "pcm":
            pcm_frame = packet
            self._write_pcm(pcm_frame)
        else:
            pcm_frame = self._decode_opus(packet)
            if pcm_frame:
                self._write_pcm(pcm_frame)

        self._rotate_if_needed()

    def close(self) -> None:
        self._finalize_segment(convert_even_if_short=True)

    def _ensure_segment_open(self) -> None:
        if self._wav_fp is not None:
            return

        os.makedirs(self.config.root_dir, exist_ok=True)
        now = datetime.now(timezone.utc).astimezone()
        date_dir = now.strftime("%Y-%m-%d")
        device_root = os.path.join(self.config.root_dir, self.device_dir, date_dir)
        os.makedirs(device_root, exist_ok=True)

        self._segment_start_epoch = int(time.time())
        ts = now.strftime("%Y%m%d_%H%M%S")
        end = now + timedelta(seconds=max(int(self.config.segment_seconds), 1))
        range_label = self._time_range_label(now, end)
        base_name = (
            f"{ts}_{self._segment_start_epoch}_{self.session_id}_"
            f"{self._segment_index}_{range_label}"
        )
        self._wav_path = os.path.join(device_root, f"{base_name}.wav")

        wav_fp = wave.open(self._wav_path, "wb")
        wav_fp.setnchannels(self.config.channels)
        wav_fp.setsampwidth(2)
        wav_fp.setframerate(self.config.sample_rate)

        self._wav_fp = wav_fp
        self._frames_written = 0

    def _write_pcm(self, pcm_frame: bytes) -> None:
        if self._wav_fp is None:
            return
        if len(pcm_frame) % 2 != 0:
            pcm_frame = pcm_frame[:-1]
        if not pcm_frame:
            return
        self._wav_fp.writeframes(pcm_frame)
        self._frames_written += len(pcm_frame) // 2 // max(self.config.channels, 1)

    def _decode_opus(self, opus_packet: bytes) -> bytes:
        try:
            if self._decoder is None:
                self._decoder = opuslib_next.Decoder(
                    self.config.sample_rate, self.config.channels
                )
            buffer_size = 960
            return self._decoder.decode(opus_packet, buffer_size)
        except Exception:
            return b""

    def _rotate_if_needed(self) -> None:
        segment_frames = self.config.segment_seconds * self.config.sample_rate
        if self._frames_written >= segment_frames:
            self._finalize_segment(convert_even_if_short=False)
            self._segment_index += 1

    def _finalize_segment(self, convert_even_if_short: bool) -> None:
        if self._wav_fp is None or self._wav_path is None:
            return

        wav_fp = self._wav_fp
        wav_path = self._wav_path
        frames_written = self._frames_written
        actual_duration_ms = int(
            (frames_written * 1000) / max(self.config.sample_rate, 1)
        )

        self._wav_fp = None
        self._wav_path = None
        self._frames_written = 0
        self._segment_start_epoch = None

        try:
            wav_fp.close()
        except Exception:
            pass

        if frames_written == 0 and not convert_even_if_short:
            try:
                os.remove(wav_path)
            except Exception:
                pass
            return

        # Rename the wav to embed the configured segment length and actual length.
        # This keeps directories clean and makes it obvious why a file is shorter
        # than the configured segment_seconds.
        try:
            base, ext = os.path.splitext(wav_path)
            renamed_wav_path = f"{base}_seg{self.config.segment_seconds}s_len{actual_duration_ms}ms{ext}"
            if renamed_wav_path != wav_path:
                os.replace(wav_path, renamed_wav_path)
                wav_path = renamed_wav_path
        except Exception:
            pass

        if not shutil.which(self.config.ffmpeg_path):
            return

        mp3_path = os.path.splitext(wav_path)[0] + ".mp3"
        try:
            subprocess.run(
                [
                    self.config.ffmpeg_path,
                    "-y",
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    wav_path,
                    "-codec:a",
                    "libmp3lame",
                    "-b:a",
                    self.config.mp3_bitrate,
                    mp3_path,
                ],
                check=True,
            )
            if not self.config.keep_wav:
                try:
                    os.remove(wav_path)
                except Exception:
                    pass
        except Exception:
            return
