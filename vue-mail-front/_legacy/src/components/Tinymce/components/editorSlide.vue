<template>
  <div class="upload-container">
    <el-button :style="{ background: color, borderColor: color }" type="primary" @click="dialogVisible = true">
      Upload slides
    </el-button>
    <el-dialog title="Upload slides" :visible.sync="dialogVisible">
      <el-upload
        class="editor-upload"
        :action="uploadAction"
        :headers="uploadHeaders"
        :multiple="true"
        :show-file-list="true"
        list-type="picture-card"
        :file-list="fileList"
        :on-remove="handleRemove"
        :on-success="handleSuccess"
      >
        <el-button size="small" type="primary">Choose files</el-button>
      </el-upload>
      <el-button @click="dialogVisible = false">Cancel</el-button>
      <el-button type="primary" @click="handleSubmit">Confirm</el-button>
    </el-dialog>
  </div>
</template>

<script>
import { resolveApiUrl } from 'utils/url';

export default {
  name: 'editorSlideUpload',
  props: {
    color: {
      type: String,
      default: '#20a0ff'
    }
  },
  data() {
    return {
      dialogVisible: false,
      list: [],
      fileList: [],
      uploadAction: process.env.BASE_API + '/api/attachments/upload'
    };
  },
  computed: {
    uploadHeaders() {
      return {
        'X-Token': this.$store.getters.token || ''
      };
    }
  },
  methods: {
    handleSuccess(response, file, fileList) {
      const data = response.data || response;
      this.fileList = fileList;
      this.list.push({
        uid: file.uid,
        url: resolveApiUrl(data.url)
      });
    },
    handleRemove(file, fileList) {
      this.fileList = fileList;
      this.list = this.list.filter(item => item.uid !== file.uid);
    },
    handleSubmit() {
      this.$emit('successCBK', this.list.map(item => item.url));
      this.list = [];
      this.fileList = [];
      this.dialogVisible = false;
    }
  }
};
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.upload-container {
  .editor-upload {
    margin-bottom: 20px;
  }
}
</style>
