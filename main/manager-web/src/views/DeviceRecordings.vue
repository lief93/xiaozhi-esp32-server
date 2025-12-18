<template>
  <div class="welcome">
    <HeaderBar />

    <div class="operation-bar">
      <h2 class="page-title">设备录音</h2>
      <div class="right-operations">
        <el-button type="text" @click="$router.back()">返回</el-button>
        <el-button size="mini" type="primary" @click="fetch()">刷新</el-button>
      </div>
    </div>

    <div class="main-wrapper">
      <div class="content-panel">
        <el-card class="device-card" shadow="never">
          <div class="meta">
            <div>AgentId: {{ agentId }}</div>
            <div>DeviceId: {{ deviceId }}</div>
          </div>

          <el-table :data="files" v-loading="loading" element-loading-background="rgba(255, 255, 255, 0.7)">
            <el-table-column label="文件名" prop="fileName" min-width="220" />
            <el-table-column label="大小" min-width="120">
              <template slot-scope="scope">
                {{ formatBytes(scope.row.size) }}
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="180">
              <template slot-scope="scope">
                {{ formatTime(scope.row.lastModified) }}
              </template>
            </el-table-column>
            <el-table-column label="播放" min-width="320">
              <template slot-scope="scope">
                <audio :src="getUrl(scope.row.fileName)" controls preload="none" style="width: 300px;" />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="120">
              <template slot-scope="scope">
                <el-button size="mini" type="text" :href="getUrl(scope.row.fileName)" target="_blank">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script>
import Api from '@/apis/api';
import HeaderBar from "@/components/HeaderBar.vue";

export default {
  components: { HeaderBar },
  data() {
    return {
      loading: false,
      files: [],
      agentId: this.$route.params.agentId,
      deviceId: this.$route.params.deviceId,
    };
  },
  mounted() {
    this.fetch();
  },
  methods: {
    fetch() {
      this.loading = true;
      Api.device.getDeviceRecordings(this.agentId, this.deviceId, ({ data }) => {
        this.loading = false;
        if (data && data.code === 0) {
          this.files = data.data || [];
        } else {
          this.$message.error((data && data.msg) || '获取录音失败');
        }
      });
    },
    getUrl(fileName) {
      return Api.device.getDeviceRecordingFileUrl(this.agentId, this.deviceId, fileName);
    },
    formatTime(ts) {
      if (!ts) return '-';
      const d = new Date(ts);
      return d.toLocaleString();
    },
    formatBytes(bytes) {
      if (!bytes && bytes !== 0) return '-';
      const units = ['B', 'KB', 'MB', 'GB'];
      let idx = 0;
      let val = bytes;
      while (val >= 1024 && idx < units.length - 1) {
        val /= 1024;
        idx += 1;
      }
      return `${val.toFixed(idx === 0 ? 0 : 1)} ${units[idx]}`;
    },
  }
};
</script>

<style scoped>
.meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #666;
  font-size: 12px;
}
</style>

