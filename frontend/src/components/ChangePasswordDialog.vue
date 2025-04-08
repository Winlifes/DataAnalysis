<template>
  <el-dialog title="修改密码" :visible.sync="visible" width="400px">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input type="password" v-model="form.oldPassword" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input type="password" v-model="form.newPassword" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input type="password" v-model="form.confirmPassword" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  props: {
    visible: Boolean,
  },
  emits: ['update:visible'],
  data() {
    return {
      form: {
        oldPassword: '',
        newPassword: '',
        confirmPassword: '',
      },
      rules: {
        oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
        newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
        confirmPassword: [
          { required: true, message: '请确认密码', trigger: 'blur' },
          {
            validator: (rule, value, callback) => {
              if (value !== this.form.newPassword) {
                callback(new Error('两次密码不一致'));
              } else {
                callback();
              }
            },
            trigger: 'blur',
          },
        ],
      },
    };
  },
  methods: {
    handleSubmit() {
      this.$refs.formRef.validate(async valid => {
        if (valid) {
          try {
            const res = await this.$axios.post('/api/user/change-password', this.form);
            this.$message.success(res.data.message || '密码修改成功');
            this.$emit('update:visible', false);
          } catch (e) {
            this.$message.error(e.response?.data?.message || '修改失败');
          }
        }
      });
    },
  },
};
</script>
