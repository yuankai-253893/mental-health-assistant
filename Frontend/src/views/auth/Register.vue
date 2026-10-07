<template>
    <div class="container">
        <div class="title">
            <div class="title-text">
                <h2>创建您的账户</h2>
                <p>请填写注册信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form label-position="top" :model="formData" :rules="rules" ref="submitFormRef">
                <el-form-item label="用户名或邮箱" prop="username">
                    <el-input v-model="formData.username" placeholder="请输入用户名" size="large" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="formData.email" placeholder="请输入邮箱" size="large" />
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="formData.nickname" placeholder="请输入昵称(可选)" size="large" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="formData.phone" placeholder="请输入手机号(可选)" size="large" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" placeholder="请输入密码" size="large" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="formData.confirmPassword" placeholder="请再次输入密码" size="large" type="password" show-password />
                </el-form-item>
                <el-form-item>
                    <el-button class="btn" type="primary" size="large" :loading="loading" @click="submitForm(submitFormRef)">注册</el-button>
                </el-form-item>
            </el-form>
        </div>
    </div>

</template>
<script setup>
import { ref, reactive } from 'vue'
import { register } from '@/api/user'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
const router = useRouter()
const formData = reactive({
    "username": "",
    "email": "",
    "nickname": "",
    "phone": "",
    "password": "",
    "confirmPassword": "",
    "gender": 0, // 性别
    "userType": 1 // 1为普通用户
})

// 两次输入的密码必须一致
const validateConfirm = (rule, value, callback) => {
    if (value !== formData.password) {
        callback(new Error('两次输入的密码不一致'))
    } else {
        callback()
    }
}

// 校验规则与后端 UserRegisterCommandDTO 保持一致
const rules = reactive({
    "username": [
        { required: true, message: "请输入用户名", trigger: "blur" },
        { min: 3, max: 50, message: "用户名长度在3到50个字符之间", trigger: "blur" },
        { pattern: /^[a-zA-Z0-9_]+$/, message: "用户名只能包含字母、数字和下划线", trigger: "blur" }
    ],
    "email": [
        { required: true, message: "请输入邮箱", trigger: "blur" },
        { type: "email", message: "邮箱格式不正确", trigger: "blur" }
    ],
    "nickname": [
        { max: 50, message: "昵称长度不能超过50个字符", trigger: "blur" }
    ],
    "phone": [
        { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }
    ],
    "password": [
        { required: true, message: "请输入密码", trigger: "blur" },
        { min: 6, max: 50, message: "密码长度在6到50个字符之间", trigger: "blur" }
    ],
    "confirmPassword": [
        { required: true, message: "请输入确认密码", trigger: "blur" },
        { validator: validateConfirm, trigger: "blur" }
    ]
})

// 表单提交
const submitFormRef = ref(null)
const loading = ref(false)

const submitForm = async (formEl) => {
    if (!formEl) return
    try {
        await formEl.validate()          // 校验失败会 reject，直接返回不发起请求
    } catch (e) {
        return
    }
    loading.value = true
    try {
        // 手机号、昵称为选填项：后端 @Pattern 会把空字符串判为不合法，
        // 因此提交前需剔除空值字段，否则留空会返回「手机号格式不正确」
        const payload = { ...formData }
        if (!payload.phone) delete payload.phone
        if (!payload.nickname) delete payload.nickname

        // 成功时 request.js 已剥离出 data.data，无需再解构 { data }
        await register(payload)
        ElMessage.success('注册成功')
        // 注册成功后跳转到登录页
        router.push('/auth/login')
    } catch (e) {
        // 失败提示已由 request.js 响应拦截器统一弹出，此处不重复提示
    } finally {
        loading.value = false
    }
}
</script>


<style scoped lang="scss">.container {
    width: 100%;
    max-width: 384px;
    .flex-box {
        display: flex;
        align-items: center;
    }
    .title {
        .title-text {
            text-align: center;
            h2 {
                font-size: 36px;
                margin-bottom: 10px;
            }
            p {
                font-size: 18px;
                color: #6b7280;
            }
        }
    }
    .form-container {
        margin-top: 30px;
        .btn {
            margin-top: 40px;
            width: 100%;
        }
        .footer {
            padding: 30px;
            text-align: center;
        }
    }
}
</style>
