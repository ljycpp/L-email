<template>
  <div class="upload-container">
    <el-button :style="{ background: color, borderColor: color }" type="primary" @click="dialogVisible = true">
      Upload video
    </el-button>
    <el-dialog title="Upload video" :visible.sync="dialogVisible">
      <el-form ref="form" :model="form" :rules="rules" label-width="140px" label-position="left">
        <el-upload
          class="editor-video-upload"
          :action="uploadAction"
          :headers="uploadHeaders"
          :show-file-list="true"
          :file-list="videoList"
          :on-success="handleVideoSuccess"
          :on-change="handleVideoChange"
        >
          <el-button size="small" type="primary">Choose video</el-button>
        </el-upload>
        <el-form-item prop="url" label="Video URL">
          <el-input v-model="form.url"></el-input>
        </el-form-item>
        <el-form-item prop="title" label="Video title">
          <el-input v-model="form.title"></el-input>
        </el-form-item>
        <el-form-item label="Cover image"></el-form-item>
        <el-upload
          class="image-uploader"
          :action="uploadAction"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleCoverSuccess"
        >
          <img v-if="form.image" :src="form.image" class="image-uploader-image">
          <i v-else class="el-icon-plus avatar-uploader-icon"></i>
        </el-upload>
      </el-form>
      <el-button @click="dialogVisible = false">Cancel</el-button>
      <el-button type="primary" @click="handleSubmit">Confirm</el-button>
    </el-dialog>
  </div>
</template>

<script>
import { resolveApiUrl } from 'utils/url';

export default {
  name: 'editorVideoUpload',
  props: {
    color: {
      type: String,
      default: '#20a0ff'
    }
  },
  data() {
    return {
      dialogVisible: false,
      videoList: [],
      uploadAction: process.env.BASE_API + '/api/attachments/upload',
      form: {
        title: '',
        url: '',
        image: ''
      },
      rules: {
        url: [{ required: true, trigger: 'blur' }],
        title: [{ required: true, trigger: 'blur' }]
      }
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
    handleVideoChange(file, fileList) {
      this.videoList = fileList.slice(-1);
    },
    handleVideoSuccess(response) {
      const data = response.data || response;
      this.form.url = resolveApiUrl(data.url);
    },
    handleCoverSuccess(response) {
      const data = response.data || response;
      this.form.image = resolveApiUrl(data.url);
    },
    handleSubmit() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          this.$message('Form is invalid');
          return false;
        }
        if (!this.form.image) {
          this.$message('Please upload a cover image');
          return false;
        }
        this.$emit('successCBK', Object.assign({}, this.form));
        this.dialogVisible = false;
        this.videoList = [];
        this.form = {
          title: '',
          url: '',
          image: ''
        };
        return true;
      });
    }
  }
};
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.upload-container {
  .editor-video-upload {
    button {
      float: left;
    }
  }

  .image-uploader {
    margin: 5px auto;
    width: 400px;
    height: 200px;
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    line-height: 200px;

    i {
      font-size: 28px;
      color: #8c939d;
    }

    .image-uploader-image {
      height: 200px;
    }
  }
}
</style>
