<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { api } from "../api/library";
import { session } from "../stores/session";

const router = useRouter();
const loading = ref(false);
const form = reactive({ email: "", password: "" });
const authDialog = ref("");
const codeSending = ref(false);
const registerForm = reactive({
  email: "",
  password: "",
  code: "",
  name: "",
  memberType: "READER",
  departmentId: null,
  positionName: "",
});
const resetForm = reactive({ email: "", password: "", code: "" });

/** 发送注册和重置密码共用的邮箱验证码。 */
async function sendCode(target) {
  codeSending.value = true;
  try {
    await api.sendLoginCode(target.email);
    ElMessage.success("验证码已发送，请检查邮箱");
  } finally {
    codeSending.value = false;
  }
}

async function register() {
  await api.register(registerForm);
  ElMessage.success(
    registerForm.memberType === "EMPLOYEE"
      ? "注册成功，请登录后申请任职审批；审批前不具备馆员权限"
      : "注册成功，请使用新账号登录",
  );
  form.email = registerForm.email;
  authDialog.value = "";
}

async function resetPassword() {
  await api.resetPassword(resetForm);
  ElMessage.success("密码已重置，请使用新密码登录");
  form.email = resetForm.email;
  authDialog.value = "";
}

async function submit() {
  loading.value = true;
  try {
    const info = await api.login(form);
    session.save(info);
    ElMessage.success(`欢迎回来，${info.name}`);
    router.push(session.isAdmin ? "/admin/books" : "/catalog");
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-intro">
      <span class="seal">阅</span>
      <p>AI LIBRARIAN</p>
      <h1>让每一次阅读<br />都有清晰的方向</h1>
      <div class="login-books" aria-hidden="true">
        <i>文学</i><i>SCIENCE</i><i>探索</i><i>思想</i><i>READING</i>
      </div>
      <blockquote>“书籍是屹立在时间汪洋大海中的灯塔。”</blockquote>
    </section>
    <section class="login-card">
      <small>WELCOME BACK</small>
      <h2>登录阅知</h2>
      <p>使用已注册的邮箱进入智慧图书馆</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱"
          ><el-input
            v-model="form.email"
            size="large"
            autocomplete="username"
            placeholder="name@example.com"
        /></el-form-item>
        <el-form-item label="密码"
          ><el-input
            v-model="form.password"
            size="large"
            type="password"
            autocomplete="current-password"
            show-password
        /></el-form-item>
        <el-button
          native-type="submit"
          type="primary"
          size="large"
          :loading="loading"
          >登录</el-button
        >
        <div class="auth-actions">
          <el-button link @click="authDialog = 'reset'">重置密码</el-button>
          <el-button link @click="authDialog = 'register'">注册</el-button>
        </div>
      </el-form>
    </section>

    <el-dialog
      :model-value="authDialog === 'register'"
      title="注册阅知账号"
      width="500px"
      @close="authDialog = ''"
    >
      <el-form label-position="top">
        <el-form-item label="注册身份">
          <el-radio-group v-model="registerForm.memberType">
            <el-radio-button value="READER">读者</el-radio-button>
            <el-radio-button value="EMPLOYEE">员工</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="registerForm.name" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="registerForm.email">
            <template #append>
              <el-button :loading="codeSending" @click="sendCode(registerForm)">
                获取验证码
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="registerForm.code" maxlength="6" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="registerForm.password"
            placeholder="至少8个字符，UTF-8不超过72字节"
            type="password"
            show-password
          />
        </el-form-item>
        <template v-if="registerForm.memberType === 'EMPLOYEE'">
          <el-form-item label="部门ID">
            <el-input-number v-model="registerForm.departmentId" :min="1" />
          </el-form-item>
          <el-form-item label="职位">
            <el-input v-model="registerForm.positionName" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="authDialog = ''">取消</el-button>
        <el-button type="primary" @click="register">完成注册</el-button>
      </template>
    </el-dialog>

    <el-dialog
      :model-value="authDialog === 'reset'"
      title="重置密码"
      width="460px"
      @close="authDialog = ''"
    >
      <el-form label-position="top">
        <el-form-item label="注册邮箱">
          <el-input v-model="resetForm.email">
            <template #append>
              <el-button :loading="codeSending" @click="sendCode(resetForm)">
                获取验证码
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="resetForm.code" maxlength="6" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="resetForm.password"
            placeholder="至少8个字符，UTF-8不超过72字节"
            type="password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="authDialog = ''">取消</el-button>
        <el-button type="primary" @click="resetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  background: #f3f5f1;
}
.login-intro {
  padding: 10vh 9vw;
  color: #e8f0eb;
  background: linear-gradient(140deg, #123b32, #245b4d);
  position: relative;
  overflow: hidden;
}
.login-intro:after {
  content: "知";
  position: absolute;
  right: -30px;
  bottom: -130px;
  font: 420px/1 Georgia;
  color: rgba(255, 255, 255, 0.035);
}
.seal {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: #dce8dd;
  color: #174438;
  font: 30px serif;
}
.login-intro p {
  letter-spacing: 0.22em;
  color: #9dbbb1;
}
.login-intro h1 {
  font:
    500 clamp(38px, 5vw, 68px)/1.3 Georgia,
    "Songti SC",
    serif;
  margin-top: 16vh;
}
.login-intro blockquote {
  margin: 55px 0 0;
  color: #a8bdb6;
}
.login-card {
  width: min(440px, 80%);
  margin: auto;
}
.login-card small {
  letter-spacing: 0.18em;
  color: #5f8678;
}
.login-card h2 {
  font:
    500 34px Georgia,
    "Songti SC",
    serif;
  margin: 12px 0;
}
.login-card > p {
  color: #76857e;
  margin-bottom: 38px;
}
.login-card .el-button {
  width: 100%;
  margin-top: 10px;
}
.auth-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
}
.auth-actions .el-button {
  width: auto;
  color: #416f61;
}
@media (max-width: 760px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .login-intro {
    display: none;
  }
  .login-card {
    padding: 50px 0;
  }
}
</style>
