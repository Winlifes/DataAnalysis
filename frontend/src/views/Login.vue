<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">🎮 游戏数据分析平台</h2>


      <el-form @submit.prevent="login">
        <el-form-item>
          <el-input
            v-model="username"
            placeholder="请输入用户名"
            :prefix-icon="icons.User"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="icons.Lock"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            @click="login"
            :loading="loading"
          >
            登录
          </el-button>
        </el-form-item>

        <p v-if="message" :class="{ success: isSuccess, error: !isSuccess }">
          {{ message }}
        </p>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { User, Lock } from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import api from "@/api/index.js";
import {icons} from "@element-plus/icons-vue/global";


export default {
  computed: {
    icons() {
      return icons
    },
    User() {
      return User
    }
  },
  components: {
    User,
    Lock,
  },
  data() {
    return {
      username: "",
      password: "",
      message: "",
      isSuccess: false,
      loading: false,
    };
  },
  methods: {
    async login() {
      this.loading = true;
      try {
        const response = await api.post("api/auth/login", {
          username: this.username,
          password: this.password,
        });

        const result = response.data;

        if (result.status === "success") {
          ElMessage.success(result.message || "登录成功");

          // 可存 token
          localStorage.setItem("token", result.token);
          localStorage.setItem("nickname", result.nickname);
          localStorage.setItem("isSuperAdmin", result.isSuperAdmin);
          localStorage.setItem("isCheck", result.isCheck);
          localStorage.setItem("isEdit", result.isEdit);
          localStorage.setItem("isExport", result.isExported);
          this.$router.push("/dashboard");
        } else {
          ElMessage.error(result.message || "用户名或密码错误");
        }
      } catch (error) {
        ElMessage.error(error.response?.data?.message || "网络错误，请稍后重试");
      } finally {
        this.loading = false;
      }
    }

  },
  mounted() {
    localStorage.removeItem("token");
    localStorage.removeItem("nickname");
    localStorage.removeItem("isSuperAdmin");
    localStorage.removeItem("isCheck");
    localStorage.removeItem("isEdit");
    localStorage.removeItem("isExport");
  },
};
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: calc(100vh - 16px);
  background: #f2f3f5;
  overflow: hidden;
}

.login-card {
  width: 360px;
  padding: 30px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
}

.title {
  text-align: center;
  margin-bottom: 20px;
  font-size: 22px;
  font-weight: bold;
  color: #333;
}

.success {
  color: #67c23a;
  text-align: center;
}

.error {
  color: #f56c6c;
  text-align: center;
}
</style>
