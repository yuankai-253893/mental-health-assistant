<template>
    <div class="frontend-layout">
        <!-- 外层整条通栏负责固定定位与白底：只给内层限宽容器加 sticky 的话，
             宽屏下两侧空白处没有背景，页面内容会从导航旁边透出来 -->
        <div class="navbar">
            <div class="navbar-container">
                <div class="brand-section">
                    <el-image style="width: 50px; height: 50px" :src="iconUrl" alt="品牌logo" class="brand-logo" />
                    <h1 class="brand-name">心理健康AI助手</h1>
                </div>
                <div class="nav-section">
                    <router-link to="/" class="nav-link">首页</router-link>
                    <router-link to="/consultation" class="nav-link" v-if="isLoggedIn">AI咨询</router-link>
                    <router-link to="/emotion-diary" class="nav-link" v-if="isLoggedIn">情绪日记</router-link>
                    <router-link to="/knowledge" class="nav-link">知识库</router-link>
                    <el-button v-if="isLoggedIn" class="logout-btn" @click="handleLogout">退出登录</el-button>
                    <template v-else>
                        <router-link to="/auth/login" class="nav-link">登录</router-link>
                        <router-link to="/auth/register" class="nav-link">
                            <el-button type="primary">注册</el-button>
                        </router-link>
                    </template>
                </div>
            </div>
        </div>
        <div class="main-content">
            <router-view></router-view>
        </div>
        <div class="footer-container">
            <div class="footer-bottom">
                <p>&copy; 2026 心理健康AI助手. All rights reserved.</p>
            </div>
        </div>
    </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { logout } from '@/api/auth'
import { useRouter } from 'vue-router'
import iconUrl from '@/assets/images/机器人.png'

const router = useRouter()

const isLoggedIn = ref(false)

// 登出
const handleLogout = () => {
    logout().catch(() => {
        // 退出接口失败（如 token 已失效）不应阻塞登出
    }).finally(() => {
        // 无论接口成功与否都清理本地登录态
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        // 跳转到登录页
        router.push('/auth/login')
    })
}

onMounted(() => {
   isLoggedIn.value = localStorage.getItem('token') !== null
})
</script>


<style scoped lang="scss">
.frontend-layout {
    background-color: #fff;

    /* 顶部导航固定：sticky 相对视口吸顶，父级 .frontend-layout 高度随内容撑开，
       所以能一直吸在顶部；z-index 保持在 Element Plus 弹层（2000+）之下 */
    .navbar {
        position: sticky;
        top: 0;
        z-index: 100;
        background-color: #fff;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    }

    .navbar-container {
        max-width: 1200px;
        height: 100%;
        margin: 0 auto;
        padding: 10px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .brand-section {
            display: flex;
            align-items: center;

            .brand-name {
                margin-left: 10px;
                font-size: 24px;
                font-weight: 600;
                color: #333;
            }
        }

        .nav-section {
            display: flex;
            align-items: center;
            gap: 40px;

            .nav-link {
                color: #4b5563;
                font-size: 16px;
                font-weight: 500;

                &:hover {
                    color: #4A90E2;
                }
            }
        }
    }

    .footer-container {
        background: #1f2937;
        color: white;
        padding: 15px 0;
        margin-top: auto;

        .footer-bottom {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 10px;
            text-align: center;
        }
    }
}
</style>
