package xiaozhi.modules.device.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import xiaozhi.modules.device.dto.DeviceRecordingFileDTO;
import xiaozhi.modules.device.service.DeviceRecordingService;

@Service
public class DeviceRecordingServiceImpl implements DeviceRecordingService {
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[a-zA-Z0-9._-]+\\.mp3$");

    @Value("${recordings.base-dir:/recordings}")
    private String recordingsBaseDir;

    @Override
    public List<DeviceRecordingFileDTO> listDeviceRecordings(String deviceId) {
        Path deviceRoot = getDeviceRoot(deviceId);
        if (!Files.exists(deviceRoot) || !Files.isDirectory(deviceRoot)) {
            return List.of();
        }

        List<Path> mp3Files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(deviceRoot)) {
            mp3Files = walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .sorted(Comparator.comparingLong(this::lastModifiedSafely).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }

        List<DeviceRecordingFileDTO> results = new ArrayList<>();
        for (Path file : mp3Files) {
            DeviceRecordingFileDTO dto = new DeviceRecordingFileDTO();
            dto.setFileName(file.getFileName().toString());
            dto.setSize(sizeSafely(file));
            dto.setLastModified(lastModifiedSafely(file));
            results.add(dto);
        }
        return results;
    }

    @Override
    public Resource getDeviceRecordingResource(String deviceId, String fileName) {
        if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            return null;
        }
        Path deviceRoot = getDeviceRoot(deviceId);
        if (!Files.exists(deviceRoot) || !Files.isDirectory(deviceRoot)) {
            return null;
        }

        // 文件实际存放在 deviceId/<date>/ 下，这里做一次递归查找（测试阶段数据量较小）
        try (Stream<Path> walk = Files.walk(deviceRoot)) {
            Path target = walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                return null;
            }
            Path normalized = target.toAbsolutePath().normalize();
            Path normalizedRoot = deviceRoot.toAbsolutePath().normalize();
            if (!normalized.startsWith(normalizedRoot)) {
                return null;
            }
            return new FileSystemResource(normalized.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private Path getDeviceRoot(String deviceId) {
        String safeDevice = sanitizeDeviceId(deviceId);
        return Paths.get(recordingsBaseDir).resolve(safeDevice).normalize();
    }

    private String sanitizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return "unknown";
        }
        return deviceId.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private long sizeSafely(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private long lastModifiedSafely(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}

