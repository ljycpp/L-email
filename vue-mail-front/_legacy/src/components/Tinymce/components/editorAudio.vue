<template>
  <div class="upload-container">
    <el-button :style="{ background: color, borderColor: color }" type="primary" @click="dialogVisible = true">
      Upload audio
    </el-button>
    <el-dialog title="Upload audio" :visible.sync="dialogVisible">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px" label-position="right">
        <el-upload
          class="editor-audio-upload"
          :action="uploadAction"
          :headers="uploadHeaders"
          :show-file-list="true"
          :file-list="audioList"
          :on-success="handleSuccess"
          :on-change="handleChange"
        >
          <el-button size="small" type="primary">Choose audio</el-button>
        </el-upload>
        <el-form-item prop="url" label="Audio URL">
          <el-input v-model="form.url"></el-input>
        </el-form-item>
        <el-form-item prop="title" label="Audio title">
          <el-input v-model="form.title"></el-input>
        </el-form-item>
        <el-form-item label="Transcript">
          <el-input v-model="form.text" type="textarea" :autosize="{ minRows: 2 }"></el-input>
        </el-form-item>
      </el-form>
      <el-button @click="dialogVisible = false">Cancel</el-button>
      <el-button type="primary" @click="handleSubmit">Confirm</el-button>
    </el-dialog>
  </div>
</template>

<script>
import { resolveApiUrl } from 'utils/url';

export default {
  name: 'editorAudioUpload',
  props: {
    color: {
      type: String,
      default: '#20a0ff'
    }
  },
  data() {
    return {
      dialogVisible: false,
      audioList: [],
      uploadAction: process.env.BASE_API + '/api/attachments/upload',
      form: {
        title: '',
        url: '',
        text: ''
      },
      rules: {
        title: [{ required: true, trigger: 'blur' }],
        url: [{ required: true, trigger: 'blur' }]
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
    handleChange(file, fileList) {
      this.audioList = fileList.slice(-1);
    },
    handleSuccess(response) {
      const data = response.data || response;
      this.form.url = resolveApiUrl(data.url);
    },
    handleSubmit() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          this.$message('Form is invalid');
          return false;
        }
        this.$emit('successCBK', Object.assign({}, this.form));
        this.dialogVisible = false;
        this.audioList = [];
        this.form = {
          title: '',
          url: '',
          text: ''
        };
        return true;
      });
    }
  }
};
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
.upload-container {
  .editor-audio-upload {
    button {
      float: left;
      margin-left: 30px;
      margin-bottom: 20px;
    }
  }
}
</style>
