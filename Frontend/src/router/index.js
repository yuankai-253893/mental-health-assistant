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
        path: 'favorites',
        component: () => import('@/views/frontend/Favorites.vue'),
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
      },
      {
        path: 'users',
        component: () => import('@/views/backend/Users.vue'),
        meta: {
          title: '用户管理',
          icon: 'UserFilled'
        }
      },
      {
        path: 'logs',
        component: () => import('@/views/backend/Logs.vue'),
        meta: {
          title: '操作日志',
          icon: 'Document'
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

// localStorage 里的 userInfo 可能不存在或不是合法 JSON，解析失败时返回 null
const parseStoredUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo')) || null
  } catch (e) {
    return null
  }
}

/**
 * 登录态自愈：token 还在有效期内、但本地 userInfo 丢了（清缓存、换浏览器、隐私模式），
 * 此时从服务端把用户信息拉回来，否则「token && userInfo」的判定会把有效登录态误判为未登录。
 */
const restoreUserInfo = async () => {
  try {
    // 动态 import 打断 router ←→ utils/request 的静态循环依赖（request.js 里 import 了 router）
    const { getCurrentUser } = await import('@/api/user')
    const userInfo = await getCurrentUser()
    if (userInfo) {
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }
    return userInfo || null
  } catch (e) {
    // 拉不到说明 token 确实失效了：响应拦截器已清理凭证并跳登录页，这里清掉残留 token
    localStorage.removeItem('token')
    return null
  }
}

// 路由前置守卫
router.beforeEach(async (to, from, next) => {
  // 检查是否有token
  const token = localStorage.getItem('token')

  let userInfo = parseStoredUserInfo()

  // token 有效但 userInfo 丢失时先自愈；登录页无需自愈，避免多打一次请求
  if (token && !userInfo && !to.path.startsWith('/auth')) {
    userInfo = await restoreUserInfo()
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
