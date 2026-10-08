<template>
    <div>
        <PageHead title="用户管理" />
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" v-loading="loading" style="width: 100%; margin-top: 25px" empty-text="暂无用户">
            <el-table-column label="ID" width="80">
                <template #default="scope">
                    <el-tag size="small" type="info">#{{ scope.row.id }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="用户" min-width="180">
                <template #default="scope">
                    <div class="user-cell">
                        <span class="user-name">{{ scope.row.nickname || scope.row.username }}</span>
                        <span class="user-account">@{{ scope.row.username }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <el-table-column prop="phone" label="手机号" width="140">
                <template #default="scope">{{ scope.row.phone || '-' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="110">
                <template #default="scope">
                    <el-tag size="small" :type="scope.row.userType === 2 ? 'danger' : 'primary'" effect="plain">
                        {{ scope.row.userTypeDisplayName || '-' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
                <template #default="scope">
                    <el-tag size="small" :type="scope.row.status === 1 ? 'success' : 'danger'">
                        {{ scope.row.statusDisplayName || '-' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="注册时间" width="160">
                <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
                <template #default="scope">
                    <!-- 不允许操作自己：禁用自己会把自己锁在后台之外 -->
                    <el-button
                        v-if="scope.row.id !== currentUserId"
                        :type="scope.row.status === 1 ? 'danger' : 'success'"
                        text
                        @click="handleToggleStatus(scope.row)"
                    >{{ scope.row.status === 1 ? '禁用' : '启用' }}</el-button>
                    <el-button type="warning" text @click="handleResetPassword(scope.row)">重置密码</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
            style="margin-top: 25px"
            v-model:current-page="pagination.currentPage"
            :page-size="pagination.size"
            layout="prev, pager, next, total"
            :total="pagination.total"
            @current-change="handleChange"
        />
    </div>
</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/backend/PageHead.vue'
import TableSearch from '@/components/backend/TableSearch.vue'
import { getUserPage, updateUserStatus, resetUserPassword } from '@/api/admin'
import { ElMessageBox, ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/format'

// 当前登录用户 id：用于隐藏「禁用自己」的入口
const currentUserId = (() => {
    try {
        return JSON.parse(localStorage.getItem('userInfo'))?.id ?? null
    } catch (e) {
        return null
    }
})()

const formItem = ref([
    { comp: 'input', prop: 'username', label: '用户名', placeholder: '请输入用户名' },
    { comp: 'input', prop: 'nickname', label: '昵称', placeholder: '请输入昵称' },
    {
        comp: 'select', prop: 'status', label: '状态', placeholder: '请选择状态', options: [
            { label: '正常', value: 1 },
            { label: '禁用', value: 0 }
        ]
    },
    {
        comp: 'select', prop: 'userType', label: '用户类型', placeholder: '请选择类型', options: [
            { label: '普通用户', value: 1 },
            { label: '管理员', value: 2 }
        ]
    }
])

const tableData = ref([])
const loading = ref(false)

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

// 保存最近一次查询条件，翻页时沿用，避免丢失筛选
const queryForm = reactive({})

// 只拼接真正填写过的条件。
// 注意不能用真值判断：status 允许取 0（禁用），'' 与 0 必须区分开
const isFilled = (val) => val !== '' && val !== null && val !== undefined

const handleSearch = async (formData) => {
    // 表单触发查询时才覆盖条件并回到第一页；翻页传 undefined，沿用上次条件
    if (formData) {
        Object.assign(queryForm, formData)
        pagination.currentPage = 1
    }

    const params = {
        currentPage: pagination.currentPage,
        size: pagination.size
    }
    if (isFilled(queryForm.username)) params.username = queryForm.username
    if (isFilled(queryForm.nickname)) params.nickname = queryForm.nickname
    if (isFilled(queryForm.status)) params.status = queryForm.status
    if (isFilled(queryForm.userType)) params.userType = queryForm.userType

    loading.value = true
    try {
        const { records, total } = await getUserPage(params)
        tableData.value = records || []
        pagination.total = total || 0
    } catch (e) {
        // 失败提示已由响应拦截器统一弹出，这里仅保证列表回到可控状态
        tableData.value = []
        pagination.total = 0
    } finally {
        loading.value = false
    }
}

const handleChange = (page) => {
    pagination.currentPage = page
    handleSearch()
}

const displayName = (row) => row.nickname || row.username || `#${row.id}`

// 禁用 / 启用
const handleToggleStatus = (row) => {
    const disabling = row.status === 1
    ElMessageBox.confirm(
        disabling
            ? `确认禁用用户「${displayName(row)}」吗？禁用后该用户已登录的会话会立即失效，且无法再次登录。`
            : `确认启用用户「${displayName(row)}」吗？启用后该用户可以正常登录。`,
        disabling ? '禁用确认' : '启用确认',
        {
            confirmButtonText: disabling ? '确认禁用' : '确认启用',
            cancelButtonText: '取消',
            type: disabling ? 'warning' : 'info'
        }
    ).then(() => {
        updateUserStatus(row.id, disabling ? 0 : 1).then(() => {
            ElMessage.success(disabling ? '已禁用' : '已启用')
            handleSearch()
        }).catch(() => {
            // 失败由拦截器提示
        })
    }).catch(() => {
        // 取消操作，无需处理
    })
}

// 重置密码
const handleResetPassword = (row) => {
    ElMessageBox.prompt('请输入新密码（6-50 个字符）', `重置「${displayName(row)}」的密码`, {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'password',
        // 与后端 UserPasswordUpdateDTO 的校验保持一致，避免白跑一趟请求
        inputValidator: (value) => {
            if (!value || !value.trim()) return '新密码不能为空'
            if (value.trim().length < 6 || value.trim().length > 50) return '密码长度需在 6-50 个字符之间'
            return true
        },
        inputErrorMessage: '密码不合法'
    }).then(({ value }) => {
        resetUserPassword(row.id, value.trim()).then(() => {
            ElMessage.success('密码已重置')
            handleSearch()
        }).catch(() => {
            // 失败由拦截器提示
        })
    }).catch(() => {
        // 取消操作，无需处理
    })
}

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
    .user-cell {
        display: flex;
        flex-direction: column;
        line-height: 1.4;

        .user-name {
            color: #333;
            font-weight: 500;
        }

        .user-account {
            font-size: 12px;
            color: #909399;
        }
    }
</style>
