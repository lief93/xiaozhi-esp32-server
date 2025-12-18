package xiaozhi.modules.device.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.device.dto.DeviceRecordingFileDTO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceRecordingService;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "设备录音")
@RestController
@RequestMapping("/device/recordings")
public class DeviceRecordingController {
    private final DeviceService deviceService;
    private final DeviceRecordingService deviceRecordingService;

    public DeviceRecordingController(DeviceService deviceService, DeviceRecordingService deviceRecordingService) {
        this.deviceService = deviceService;
        this.deviceRecordingService = deviceRecordingService;
    }

    @GetMapping("/{agentId}/{deviceId}")
    @Operation(summary = "按设备列出录音文件")
    @RequiresPermissions("sys:role:normal")
    public Result<List<DeviceRecordingFileDTO>> list(@PathVariable String agentId, @PathVariable String deviceId) {
        UserDetail user = SecurityUser.getUser();
        List<DeviceEntity> devices = deviceService.getUserDevices(user.getId(), agentId);
        boolean allowed = devices.stream()
                .anyMatch(d -> deviceId != null && deviceId.equalsIgnoreCase(d.getMacAddress()));
        if (!allowed) {
            return new Result<List<DeviceRecordingFileDTO>>().error(401, "无权限访问该设备录音");
        }

        return new Result<List<DeviceRecordingFileDTO>>().ok(deviceRecordingService.listDeviceRecordings(deviceId));
    }

    @GetMapping("/{agentId}/{deviceId}/file/{fileName:.+}")
    @Operation(summary = "播放/下载录音文件")
    @RequiresPermissions("sys:role:normal")
    public ResponseEntity<Resource> file(
            @PathVariable String agentId,
            @PathVariable String deviceId,
            @PathVariable String fileName) {
        UserDetail user = SecurityUser.getUser();
        List<DeviceEntity> devices = deviceService.getUserDevices(user.getId(), agentId);
        boolean allowed = devices.stream()
                .anyMatch(d -> deviceId != null && deviceId.equalsIgnoreCase(d.getMacAddress()));
        if (!allowed) {
            return ResponseEntity.status(401).build();
        }

        Resource res = deviceRecordingService.getDeviceRecordingResource(deviceId, fileName);
        if (res == null || !res.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.valueOf("audio/mpeg"))
                .body(res);
    }
}
