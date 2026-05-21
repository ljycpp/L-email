<template>
  <div class="login-container">
    <el-form
      ref="loginForm"
      autoComplete="on"
      :model="loginForm"
      :rules="loginRules"
      label-position="left"
      label-width="0px"
      class="card-box login-form"
    >
      <h3 class="title">Mail System Login</h3>
      <el-form-item prop="email">
        <span class="svg-container"><icon-svg icon-class="mail"/></span>
        <el-input
          v-model="loginForm.email"
          name="email"
          type="text"
          autoComplete="on"
          placeholder="Email"
        ></el-input>
      </el-form-item>
      <el-form-item prop="password">
        <span class="svg-container"><icon-svg icon-class="lock1"/></span>
        <el-input
          v-model="loginForm.password"
          name="password"
          type="password"
          autoComplete="on"
          placeholder="Password"
          @keyup.enter.native="handleLogin"
        ></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" style="width: 100%;" :loading="loading" @click.native.prevent="handleLogin">
          Login
        </el-button>
      </el-form-item>
      <span class="forget-pwd">
        Password reset is not available in the demo build.
      </span>
      <div class="tips">Demo account: admin@mail.com Password: 123456</div>
    </el-form>
    <el-dialog title="Third Party Login" :visible.sync="showDialog">
      Email login success, please choose third party verification.
      <socialSign></socialSign>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import { isEmail } from 'utils/validate';
import socialSign from './socialsignin';

export default {
  name: 'login',
  components: { socialSign },
  data() {
    const validateEmail = (rule, value, callback) => {
      if (!isEmail(value)) {
        callback(new Error('Please input a valid email'));
      } else {
        callback();
      }
    };
    const validatePass = (rule, value, callback) => {
      if (!value || value.length < 6) {
        callback(new Error('Password must be at least 6 characters'));
      } else {
        callback();
      }
    };
    return {
      loginForm: {
        email: 'admin@mail.com',
        password: ''
      },
      loginRules: {
        email: [
          { required: true, trigger: 'blur', validator: validateEmail }
        ],
        password: [
          { required: true, trigger: 'blur', validator: validatePass }
        ]
      },
      loading: false,
      showDialog: false
    };
  },
  computed: {
    ...mapGetters([
      'auth_type'
    ])
  },
  methods: {
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (!valid) {
          return false;
        }
        this.loading = true;
        this.$store.dispatch('LoginByEmail', this.loginForm).then(() => {
          this.loading = false;
          this.$router.push({ path: '/' });
        }).catch(err => {
          this.$message.error(err);
          this.loading = false;
        });
        return true;
      });
    },
    afterQRScan() {}
  }
};
</script>

<style rel="stylesheet/scss" lang="scss">
@import "src/styles/mixin.scss";

.tips {
  font-size: 14px;
  color: #fff;
  margin-bottom: 5px;
}

.login-container {
  @include relative;
  height: 100vh;
  background-color: #2d3a4b;

  input:-webkit-autofill {
    -webkit-box-shadow: 0 0 0 1000px #293444 inset !important;
    -webkit-text-fill-color: #fff !important;
  }

  input {
    background: transparent;
    border: 0;
    -webkit-appearance: none;
    border-radius: 0;
    padding: 12px 5px 12px 15px;
    color: #eeeeee;
    height: 47px;
  }

  .el-input {
    display: inline-block;
    height: 47px;
    width: 85%;
  }

  .svg-container {
    padding: 6px 5px 6px 15px;
    color: #889aa4;
  }

  .title {
    font-size: 26px;
    font-weight: bold;
    color: #eeeeee;
    margin: 0 auto 40px auto;
    text-align: center;
  }

  .login-form {
    position: absolute;
    left: 0;
    right: 0;
    width: 400px;
    padding: 35px 35px 15px 35px;
    margin: 120px auto;
  }

  .el-form-item {
    border: 1px solid rgba(255, 255, 255, 0.1);
    background: rgba(0, 0, 0, 0.1);
    border-radius: 5px;
    color: #454545;
  }

  .forget-pwd {
    color: #fff;
  }
}
</style>
