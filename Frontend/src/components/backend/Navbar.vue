<template>
    <div class="navbar">
        <div class="flex-box">
            <el-button @click="handleCollapes">
                <el-icon><Expand /></el-icon>
            </el-button>
            <p class="page-title">{{ route.meta.title }}</p>
        </div>
        <div class="flex-box">
            <el-dropdown @command="handleCommand">
                <div class="flex-box">
                    <el-avatar src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"/>
                    <p class="user-name">{{ userName }}</p>
                    <el-icon><ArrowDown /></el-icon>
                </div>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, Expand } from '@element-plus/icons-vue'
import { useAdminStore } from '@/stores/admin'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { logout } from '@/api/auth'

const router = useRouter()
const route = useRoute()

// localStorage 中的 userInfo 可能缺失或不是合法 JSON，解析失败时兜底为空对象
const userInfo = (() => {
    try {
        return JSON.parse(localStorage.getItem('userInfo')) || {}
    } catch (e) {
        return {}
    }
})()
const userName = computed(() => userInfo.nickname || userInfo.username || '管理员')

const handleCommand = (command) => {
    if (command === 'logout') {
        // 退出登录逻辑
        ElMessageBox.confirm('确定退出登录吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            logout().catch(() => {
                // 退出接口失败（如 token 已失效）不应阻塞登出
            }).finally(() => {
                // 无论接口成功与否都清理本地登录态，避免卡在“伪登录”状态
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                router.push('/auth/login')
            })
        }).catch(() => {
            // 用户取消，无需处理
        })
    }
}

const handleCollapes = () => {
    const adminStore = useAdminStore()
    adminStore.toggleCollapse()
}
</script>

<style lang="scss" scoped>
.navbar {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 15px;
    background: white;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #e5e7eb;
    .flex-box {
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .page-title {
        margin-left: 20px;
        font-size: 20px;
        font-weight: bold;
        color: #1f2937;
    }
}

</style>
