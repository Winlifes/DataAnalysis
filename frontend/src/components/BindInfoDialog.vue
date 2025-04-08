<template>
  <el-dialog :title="'绑定' + type" :visible.sync="visible" width="400px">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item :label="label" prop="value">
        <el-input v-model="form.value" />
      </el-form-item>
      <el-form-item label="验证码" prop="code">
        <el-input v-model="form.code" />
        <el-button size="small" type="primary" @click="sendCode" :disabled="sending">{{ sending ? '已发送' : '发送验证码' }}</el-button>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script>
import axios from "axios";

export default {
  props: {
    visible: Boolean,
    type: String, // '手机' 或 '邮箱'
  },
  emits: ['update:visible'],
  data() {
    return {
      form: {
        value: '',
        code: '',
      },
      sending: false,
      rules: {
        value: [{ required: true, message: '不能为空', trigger: 'blur' }],
        code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
      },
    };
  },
  computed: {
    label() {
      return this.type === '手机' ? '手机号' : '邮箱';
    },
  },
  methods: {
    async sendCode() {
      this.sending = true;
      try {
        await axios.post(`/api/user/send-code`, {
          type: this.type === '手机' ? 'phone' : 'email',
          target: this.form.value,
        });
        this.$message.success('验证码发送成功');
      } catch {
        this.$message.error('验证码发送失败');
      } finally {
        this.sending = false;
      }
    },
    handleSubmit() {
      this.$refs.formRef.validate(async valid => {
        if (valid) {
          try {
            await axios.post(`/api/user/bind-${this.type === '手机' ? 'phone' : 'email'}`, this.form);
            this.$message.success('绑定成功');
            this.$emit('update:visible', false);
          } catch {
            this.$message.error('绑定失败');
          }
        }
      });
    },
  },
};
</script>
