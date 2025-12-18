package xiaozhi.modules.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "设备录音文件")
public class DeviceRecordingFileDTO {
    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private long size;

    @Schema(description = "最后修改时间（毫秒时间戳）")
    private long lastModified;
}

