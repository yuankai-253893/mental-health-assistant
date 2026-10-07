<template>
    <el-aside :width="isCollapse ? '64px' : '264px'">
        <el-menu
            :default-active="activeIndex"
            class="menu-style"
            :collapse="isCollapse"          
            :collapse-transition="false"
        >
            <div class="brand">
                <el-image style="width: 50px; height: 50px; margin-right: 10px;" :src="iconUrl" alt="logo" />
                <div v-show="!isCollapse" class="info-card">
                    <h1 class="brand-title">心理健康AI助手</h1>
                    <p class="brand-subtitle">管理后台</p>
                </div>
            </div>

            <!-- 路由菜单 -->
            <el-menu-item @click="selectMenu" v-for="item in router.options.routes[0].children" :key="item.path" :index="item.path">
                <el-icon><component :is="item.meta.icon" /></el-icon>
                <span>{{ item.meta.title }}</span>  
            </el-menu-item>
        </el-menu>
    </el-aside>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import iconUrl from '@/assets/images/机器人.png'
import { useAdminStore } from '@/stores/admin'
import { computed } from 'vue'

const adminStore = useAdminStore()
const isCollapse = computed(() => adminStore.isCollapse)

const router = useRouter()
const route = useRoute()

// 菜单项 index 用的是子路由 path（如 "dashboard"），
// 由当前路由推导出应高亮的项，替代原先写死的 default-active="2"
const activeIndex = computed(() => route.path.replace('/back/', ''))

const selectMenu = (key) => {
    const currentroute = router.options.routes[0]
    router.push(`${currentroute.path}/${key.index}`)
}
</script>

<style lang="scss" scoped>
.menu-style {
    height: 100%;
    .brand {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 10px;
    background-color: #fff;
    border-bottom: 1px solid #e5e7ed;
    .info-card {
        .brand-title {
            font-size: 20px;
            font-weight: bold;
            margin-bottom: 5px;
            color: #1f2937;
        }
        .brand-subtitle {
            font-size: 14px;
            color: #6b7280;
        }
    }
}
}
</style>
