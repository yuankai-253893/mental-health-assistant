import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '@/components/layouts/BackendLayout.vue'
import AuthLayout from '@/components/layouts/AuthLayout.vue'
import FrontendLayout from '@/components/layouts/FrontendLayout.vue'

const frontendRoutes = [
  {
    path: '/',
    redirect: '/home',
    component: FrontendLayout,
    children: [
      {
        path: 'home',
        component: () => import('@/views/frontend/Home.vue'),
      },
      {
        path: 'consultation',
        component: () => import('@/views/frontend/Consultation.vue'),
      },
      {
        path: 'emotion-diary',
        component: () => import('@/views/frontend/EmotionDiary.vue'),
      },
      {
        path: 'knowledge',
        component: () => import('@/views/frontend/Knowledge.vue'),
      },
      {
        // 知识文章详情，props: true 把 :id 直接透传给 ArticleDetail 的 props
        path: 'knowledge/article/:id',
        name: 'articleDetail',
        component: () => import('@/views/frontend/ArticleDetail.vue'),
        props: true,
      },
    ]
  }
]


// 路由配置
const backendRoutes = [
  {
    // 后端嵌套路由
    path: '/back',
    redirect: '/back/dashboard',
    component: BackendLayout,
    children: [
      {
        path: 'dashboard',
        component: () => import('@/views/backend/Dashboard.vue'),
        meta: {
          title: '数据分析',
          icon: 'PieChart'
        }
      },
      {
        path: 'knowledge',
        component: () => import('@/views/backend/Knowledge.vue'),
        meta: {
          title: '知识文章',
          icon: 'ChatLineSquare'
        }
      },
      {
        path: 'consultations',
        component: () => import('@/views/backend/Consultations.vue'),
        meta: {
          title: '咨询记录',
          icon: 'Message'
        }
      },
      {
        path: 'emotional',
        component: () => import('@/views/backend/Emotional.vue'),
        meta: {
          title: '情绪日志',
          icon: 'User'
        }
      }
    ]
  },
  {
    path: '/auth',
    component: AuthLayout,
    children: [
      {
        path: 'login',
        name: 'login',
        component: () => import('@/views/auth/Login.vue'),
        meta: {
          title: '登录',
        }
      },
      {
        path: 'register',
        component: () => import('@/views/auth/Register.vue'),
        meta: {
          title: '注册',
        }
      }
    ]
    
  }

]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),    // 路由模式：history
  routes: [...backendRoutes, ...frontendRoutes],     // 路由配置
})

// 路由前置守卫
router.beforeEach((to, from, next) => {
  // 检查是否有token
  const token = localStorage.getItem('token')
  // userInfo 可能不存在或不是合法 JSON，直接 JSON.parse 会抛错并中断导航
  let userInfo = null
  try {
    userInfo = JSON.parse(localStorage.getItem('userInfo'))
  } catch (e) {
    userInfo = null
  }

  // token 与 userInfo 同时存在才算已登录
  if (token && userInfo) {
    // 如果是后台用户
    if (userInfo.userType == 2) {
      if (to.path.startsWith('/back')) {
        next()
      } else {
        next('/back/dashboard')
      }
    } else if (userInfo.userType == 1) {
      // 用户端账号只能访问前端页面
      if (to.path.startsWith('/back') || to.path.startsWith('/auth')) {
        next('/home')
      } else {
        next()
      }
    } else {
      // userType 不是已知取值时按未登录处理，保证每条分支都调用 next()
      next(to.path.startsWith('/back') ? '/auth/login' : undefined)
    }
  } else {
    // 没登录（或登录信息不完整）
    if (to.path.startsWith('/back')) {
      // 如果是访问后端页面，跳转到登录页
      next('/auth/login')
    } else {
      next()
    }
  }
})

export default router
