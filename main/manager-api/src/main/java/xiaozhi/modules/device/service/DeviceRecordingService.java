package xiaozhi.modules.device.service;

import java.util.List;

import org.springframework.core.io.Resource;

import xiaozhi.modules.device.dto.DeviceRecordingFileDTO;

public interface DeviceRecordingService {
    List<DeviceRecordingFileDTO> listDeviceRecordings(String deviceId);

    Resource getDeviceRecordingResource(String deviceId, String fileName);
}

