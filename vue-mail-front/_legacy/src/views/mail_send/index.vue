<template>
  <div v-loading.body="loading" class="app-container calendar-list-container">
    <el-row :gutter="10">
      <el-col :span="2">
        <el-tag class="target-label" color="#36c6d3">To</el-tag>
      </el-col>
      <el-col :span="20">
        <multiselect
          v-model="target"
          :options="contacts"
          :multiple="true"
          :taggable="true"
          @tag="addContact"
          :clear-on-select="false"
          :hide-target="true"
          placeholder="Select or input contacts"
          label="show"
          track-by="mail"
        ></multiselect>
      </el-col>
      <el-col :span="2">
        <el-button type="danger" @click="cleanTarget">Clear</el-button>
      </el-col>
    </el-row>
    <el-row :gutter="10">
      <el-col :span="2">
        <el-tag class="target-label" color="#36c6d3">Cc</el-tag>
      </el-col>
      <el-col :span="20">
        <multiselect
          v-model="copy"
          :options="contacts"
          :multiple="true"
          :taggable="true"
          @tag="addContact"
          :clear-on-select="false"
          :hide-target="true"
          placeholder="Select or input contacts"
          label="show"
          track-by="mail"
        ></multiselect>
      </el-col>
      <el-col :span="2">
        <el-button type="danger" @click="cleanCopy">Clear</el-button>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="2">
        <el-tag class="target-label" color="#36c6d3">Subject</el-tag>
      </el-col>
      <el-col :span="20">
        <el-input v-model="mail.title" placeholder="Input subject"></el-input>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-upload
          class="upload-file"
          :headers="uploadHeaders"
          :before-upload="handleBefore"
          :on-preview="handlePreview"
          :on-success="handleSuccess"
          :on-remove="handleRemove"
          :show-file-list="true"
          :file-list="mail.oldFileList"
          drag
          :action="uploadAction"
          multiple
        >
          <i class="el-icon-upload"></i>
          <i class="el-upload__text">
            Drop file here, or <em>click upload</em>
          </i>
        </el-upload>
      </el-col>
      <el-col :span="12">
        <el-button type="primary" :disabled="isRecording" @click="startRecorder" size="small">Start record</el-button>
        <el-button type="primary" :disabled="!isRecording" @click="stopRecorder" size="small">Stop record</el-button>
        <ul v-show="!!mail.oldAudioList.length" class="old-audio-list">
          <li v-for="(audio, index) in mail.oldAudioList" :key="'old-audio-' + index">
            <audio :src="audio.url" controls></audio>
            <a class="old-audio-name" :href="audio.url" download>{{audio.name}}</a>
            <icon-svg icon-class="delete11" class="del-audio" @click.native="delOldAudio(index)"/>
          </li>
        </ul>
        <ul class="audio-list">
          <li v-for="(audio, index) in mail.audioList" :key="'audio-' + index">
            <audio :src="audio.url" controls></audio>
            <el-input class="audio-name" v-model="audio.name" size="small"></el-input>
            <icon-svg icon-class="delete11" class="del-audio" @click.native="delAudio(index)"/>
          </li>
        </ul>
      </el-col>
    </el-row>
    <div class="editor-container">
      <Tinymce :id="editorId" :height="editorHeight" ref="editor" v-model="mail.content"></Tinymce>
    </div>
    <el-row>
      <el-col :span="12" :offset="9">
        <el-button type="primary" @click="sendSubmit">Send</el-button>
        <el-button type="primary" @click="saveAsDraft">Save</el-button>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import Tinymce from 'components/Tinymce';
import * as contactsAPI from 'api/mail_contacts';
import * as mailSendAPI from 'api/mail_send';
import * as mailDetailAPI from 'api/mail_detail';
import { isEmail } from 'utils/validate';
import { getNowFormatDate, parseTime } from 'utils';
import { Observable } from 'rxjs/Observable';
import { resolveApiUrl } from 'utils/url';

export default {
  name: 'mail_send',
  components: { Tinymce },
  data() {
    return {
      loading: true,
      mail: {
        title: '',
        oldFileList: [],
        oldAudioList: [],
        content: '',
        fileList: [],
        audioList: []
      },
      date: '',
      target: [],
      copy: [],
      isRecording: false,
      recorder: null,
      contacts: [],
      editorId: 'mail_send_ediotr',
      editorHeight: null,
      uploadAction: process.env.BASE_API + '/api/attachments/upload',
      uploadHeaders: {}
    };
  },
  created() {
    this.initSendPage();
  },
  methods: {
    initSendPage() {
      this.uploadHeaders = {
        'X-Token': this.$store.getters.token || ''
      };
      this.getContacts().subscribe({
        next: () => this.getContent()
      });
      this.editorHeight = window.innerHeight - 420;
    },
    getContacts() {
      this.loading = true;
      return Observable.create(observer => {
        contactsAPI.fetchList().then(res => {
          res.data.contacts.forEach(item => {
            item.show = item.name + '<' + item.mail + '>';
          });
          this.contacts = res.data.contacts;
          this.loading = false;
          observer.next();
        }).catch(err => observer.error(err));
      });
    },
    getContent() {
      const pageType = this.$store.getters.pageType;
      const mailId = this.$store.getters.mailId || this.$store.getters.draftId;
      const mailType = this.$store.getters.mailType;
      if (pageType && pageType !== 'add') {
        mailDetailAPI.fetchDetail({ mailId, mailType }).then(res => {
          const detail = res.data;
          this.mail.title = detail.title;
          this.mail.content = detail.content;
          this.mail.oldFileList = (detail.oldFileList || []).map((item, index) => Object.assign({ uid: 'old-file-' + index }, item, {
            url: resolveApiUrl(item.url)
          }));
          this.mail.oldAudioList = (detail.oldAudioList || []).map(item => Object.assign({}, item, {
            url: resolveApiUrl(item.url)
          }));
          this.date = detail.receiveDate || detail.sendDate;

          let receiveStr = '';
          (detail.target || []).forEach(item => {
            item.show = item.name + '<' + item.mail + '>';
            receiveStr += item.show + ';';
          });
          let copyStr = '';
          (detail.copy || []).forEach(item => {
            item.show = item.name + '<' + item.mail + '>';
            copyStr += item.show + ';';
          });
          const sender = {
            name: detail.sender,
            mail: detail.sendMail,
            show: detail.sender + '<' + detail.sendMail + '>'
          };

          switch (pageType) {
            case 'reply':
              this.mail.title = 'Re: ' + this.mail.title;
              this.addContentHeader(sender.show, receiveStr, copyStr);
              this.target = [sender];
              break;
            case 'replyAll':
              this.mail.title = 'Re: ' + this.mail.title;
              this.addContentHeader(sender.show, receiveStr, copyStr);
              this.target = [sender].concat(detail.target || [], detail.copy || []);
              break;
            case 'edit':
              this.target = detail.target || [];
              this.copy = detail.copy || [];
              break;
            case 'forward':
              this.mail.title = 'Fwd: ' + this.mail.title;
              this.addContentHeader(sender.show, receiveStr, copyStr);
              break;
            default:
          }
        });
      } else if (pageType === 'add') {
        const target = this.$store.getters.target;
        if (target) {
          if (target === 'all') {
            this.target = this.contacts;
          } else {
            target.forEach(item => {
              item.show = item.name + '<' + item.mail + '>';
            });
            this.target = target;
          }
          this.$store.commit('SET_TARGET', null);
        }
      }
    },
    addContentHeader(sender, receiveStr, copyStr) {
      const header = `<p><span>------------------------ Original Mail ------------------------</span></p>
        <div style="background: #e4eaef"><br>
        <p><strong>From:</strong> ${sender}</p>
        <p><strong>Time:</strong> ${parseTime(this.date)}</p>
        <p><strong>To:</strong> ${receiveStr}</p>
        <p><strong>Cc:</strong> ${copyStr}</p>
        <p><strong>Subject:</strong> ${this.mail.title}</p>
        <p><br/></p>
        </div>`;
      this.mail.content = header + this.mail.content;
    },
    addContact(newTag) {
      if (!isEmail(newTag)) {
        this.$message({
          showClose: true,
          message: 'Invalid email address',
          type: 'warning',
          duration: 1200
        });
        return;
      }
      const tag = {
        name: newTag,
        show: newTag,
        mail: newTag
      };
      this.contacts.push(tag);
      this.target.push(tag);
    },
    cleanTarget() {
      this.target = [];
    },
    cleanCopy() {
      this.copy = [];
    },
    handleBefore() {
      return true;
    },
    handleSuccess(response, file) {
      const data = response.data || response;
      const uploadedFile = {
        id: data.id,
        name: data.name || file.name,
        url: resolveApiUrl(data.url),
        uid: file.uid
      };
      this.mail.fileList.push(uploadedFile);
      this.mail.oldFileList.push(uploadedFile);
    },
    handlePreview(file) {
      window.open(resolveApiUrl(file.url));
    },
    handleRemove(file) {
      this.mail.fileList = this.mail.fileList.filter(item => item.uid !== file.uid && item.id !== file.id);
      this.mail.oldFileList = this.mail.oldFileList.filter(item => item.uid !== file.uid && item.id !== file.id);
    },
    startRecorder() {
      Recorder.get(rec => {
        this.recorder = rec;
        this.recorder.start();
        this.isRecording = true;
      });
    },
    stopRecorder() {
      this.recorder.stop();
      this.isRecording = false;
      this.mail.audioList.push({
        name: getNowFormatDate(),
        blob: this.recorder.getBlob(),
        url: window.URL.createObjectURL(this.recorder.getBlob())
      });
    },
    delOldAudio(index) {
      this.mail.oldAudioList.splice(index, 1);
    },
    delAudio(index) {
      this.mail.audioList.splice(index, 1);
    },
    buildLegacyFormData(includeDraftId) {
      const form = new FormData();
      form.append('title', this.mail.title || '');
      form.append('content', this.mail.content || '');
      if (includeDraftId && this.$store.getters.mailType === 'draft' && this.$store.getters.draftId) {
        form.append('draftId', this.$store.getters.draftId);
      }
      this.target.forEach((item, index) => {
        form.append(`target[${index}]`, item.mail);
      });
      this.copy.forEach((item, index) => {
        form.append(`copy[${index}]`, item.mail);
      });
      this.mail.fileList.forEach((item, index) => {
        if (item.id) {
          form.append(`attachmentIds[${index}]`, item.id);
        }
      });
      this.mail.audioList.forEach((audio, index) => {
        form.append(`audioFile[${index}]`, audio.blob, (audio.name || `audio-${index}`) + '.webm');
      });
      return form;
    },
    sendSubmit() {
      if (this.target.length < 1) {
        this.$message({
          showClose: true,
          message: 'Receiver is required',
          type: 'warning',
          duration: 1200
        });
        return;
      }
      const mailForm = this.buildLegacyFormData(false);
      this.loading = true;
      mailSendAPI.sendMail(mailForm).subscribe({
        next: () => {
          this.$message({
            type: 'success',
            message: 'Send success',
            duration: 1200
          });
          this.loading = false;
          this.initMail();
        },
        error: () => {
          this.$message({
            type: 'error',
            message: 'Send failed',
            duration: 2000
          });
          this.loading = false;
        }
      });
    },
    saveAsDraft() {
      const mailForm = this.buildLegacyFormData(true);
      this.loading = true;
      mailSendAPI.saveAsDraft(mailForm).subscribe({
        next: () => {
          this.loading = false;
          this.$message('Saved');
        },
        error: () => {
          this.loading = false;
          this.$message('Save failed');
        }
      });
    },
    initMail() {
      this.mail = {
        title: '',
        oldFileList: [],
        oldAudioList: [],
        content: '',
        fileList: [],
        audioList: []
      };
      this.target = [];
      this.copy = [];
      tinymce.get(this.editorId).setContent('');
    }
  }
};
</script>

<style>
* {
  padding: 0;
  margin: 0;
}

ul {
  list-style: none;
}

audio {
  width: 260px;
}

.tool-bar {
  margin-top: -20px;
  margin-left: -20px;
}

.el-row {
  margin-bottom: 10px;
}

.target-label {
  font-size: 14px;
  padding: 0px 12px;
  margin: 2px 0;
  height: 35px;
  line-height: 35px;
}

.upload-file {
  display: inline-block;
  vertical-align: middle;
}

.el-upload-dragger {
  height: 30px;
}

.el-upload-dragger .el-icon-upload {
  font-size: 30px;
  line-height: 20px;
  margin: 3px 0;
}

.audio-name {
  width: 200px;
  vertical-align: 12px;
}

.audio-list > li {
  display: flex;
  align-items: center;
}

.del-audio {
  margin-left: 5px;
  cursor: pointer;
  vertical-align: 10px;
  font-size: 20px;
  color: #00adb5;
}

.old-audio-name {
  vertical-align: 10px;
}
</style>
