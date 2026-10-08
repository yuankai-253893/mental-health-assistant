<template>
    <div>
        <PageHead title="知识文章">
            <template #buttons>
                <el-button @click="handleEdit({})" type="primary">新增</el-button>
            </template>
        </PageHead>
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" style="width: 100%;margin-top: 25px">
            <el-table-column width="450" label="文章标题" fixed="left">
                <template #default="scope">
                    <div style="display: flex; align-items: center">
                        <el-icon><timer /></el-icon>
                        <span>{{ scope.row.title }}</span>
                    </div>
                </template>
            </el-table-column>
             <el-table-column  label="分类" width="200">
                <template #default="scope">
                    <div style="display: flex; align-items: center">
                        <el-icon><timer /></el-icon>
                        <span>{{ categoryMap[scope.row.categoryId] }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="authorName" label="作者" width="150" />
            <el-table-column prop="readCount" label="阅读量" width="150" />
            <el-table-column prop="publishAt" label="发布时间" width="150" :formatter="(row) => formatDateTime(row.publishAt)" />
            <el-table-column  label="操作" width="240" fixed="right">
                <template #default="scope">
                    <el-button @click="handleEdit(scope.row)" text type="primary">编辑</el-button>
                    <el-button @click="handlePublish(scope.row)" v-if="scope.row.status === 0 || scope.row.status === 2" text type="success">发布</el-button>
                    <el-button @click="handleUnpublish(scope.row)" v-if="scope.row.status === 1" text type="warning">下线</el-button>
                    <el-button @click="handleDelete(scope.row)" text type="danger">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination
         style="margin-top: 25px"
         v-model:current-page="pagination.currentPage"
         :page-size="pagination.size"
         layout="prev, pager, next"
         :total="pagination.total"
         @current-change="handleChange"
         />
         <ArticleDialog v-model:modelValue="dialogVisible" :article="currentArticle" :categories="categories" @success="handleSuccess" />
    </div>


</template>
<script setup>
import { onMounted, ref, reactive } from 'vue'
import PageHead from '@/components/backend/PageHead.vue'
import TableSearch from '@/components/backend/TableSearch.vue'
import { getCategoryTree, getAdminArticlePage, getArticleDetail, changeArticleStatus, deleteArticle } from '@/api/knowledge'
import ArticleDialog from '@/components/backend/ArticleDialog.vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/format'


const formItem = ref([
    { comp: 'input', prop: 'title', label: '文章标题', placeholder: '请输入文章标题' },
    { comp: 'select', prop: 'categoryId', label: '分类', placeholder: '请选择分类', options: [] },
    {
        comp: 'select', prop: 'status', label: '状态', placeholder: '请输入文章内容', options: [{
       label: '草稿',
       value: '0'
    },{
       label: '已发布',
       value: '1'
    },{
       label: '已下线',
       value: '2'
    }] },
    // 后端做的是「用户名 → 作者ID → 精确匹配」，因此需要输入完整用户名，不支持模糊
    { comp: 'input', prop: 'authorName', label: '作者用户名', placeholder: '请输入完整用户名（精确匹配）' }
])

// 分页参数
const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 0
})

// 记住最近一次查询条件，翻页时才能保留筛选
let lastSearchForm = {}

const handleSearch = async (formData = lastSearchForm, resetPage = true) => {
    lastSearchForm = formData || {}
    // 条件变化后应从第一页开始查询
    if (resetPage) pagination.currentPage = 1
    const params = {
        currentPage: pagination.currentPage,
        size: pagination.size,
        ...lastSearchForm
    }

    try {
        const { records, total } = await getAdminArticlePage(params)
        tableData.value = records
        pagination.total = total
    } catch (e) {
        tableData.value = []
        pagination.total = 0
    }
}

const handleChange = (page) => {
    pagination.currentPage = page
    // 翻页时沿用上次查询条件，且不要重置页码
    handleSearch(undefined, false)
}

// 分类映射
const categoryMap = reactive({})
// 分类列表
const categories = ref([])

// 列表数据
const tableData = ref([])

// 新增和编辑
const dialogVisible = ref(false)
const currentArticle = ref(null)
const handleSuccess = () => {
    dialogVisible.value = false
    // 刷新列表
    handleSearch()
}
const handleEdit = (row) => {
    if (!row.id) {
        // 新增
        currentArticle.value = null
        dialogVisible.value = true

    } else {
        // 编辑
        getArticleDetail(row.id).then(res => {
            currentArticle.value = res
            dialogVisible.value = true
        }).catch(() => {
            // 详情加载失败已由拦截器提示
        })
    }
    
}

// 发布
const handlePublish = (row) => {
    ElMessageBox.confirm(
        `确认发布文章${row.title}吗？`,
        '确认',
        {
            confirmButtonText: '确认发布',
            cancelButtonText: '取消',
            type: 'info'
        }
    ).then(() => {
        changeArticleStatus(row.id, { status: 1 }).then(res => {
            ElMessage.success('发布成功')
            handleSearch()
        }).catch(() => {
            // 失败已由拦截器提示
        })
    })
}

const handleUnpublish = (row) => {
    ElMessageBox.confirm(
        `确认下线文章${row.title}吗？`,
        '确认',
        {
            confirmButtonText: '确认下线',
            cancelButtonText: '取消',
            type: 'warning'
        }
    ).then(() => {
        changeArticleStatus(row.id, { status: 2 }).then(res => {
            ElMessage.success('下线成功')
            handleSearch()
        }).catch(() => {
            // 失败已由拦截器提示
        })
    })
}

const handleDelete = (row) => {
    ElMessageBox.confirm(
        `确认删除文章${row.title}吗？`,
        '确认',
        {
            confirmButtonText: '确认删除',
            cancelButtonText: '取消',
            type: 'danger'
        }
    ).then(() => {
        deleteArticle(row.id).then(res => {
            ElMessage.success('删除成功')
            handleSearch()
        }).catch(() => {
            // 失败已由拦截器提示
        })
    })
}

onMounted(async () => {
    // 分类加载失败不应阻塞列表请求
    try {
        const data = await getCategoryTree()

        categories.value = data.map(item => {
            categoryMap[item.id] = item.categoryName
            return {
                label: item.categoryName,
                value: item.id
            }
        })
        formItem.value[1].options = categories.value
    } catch (e) {
        // 分类加载失败不阻塞列表，下拉框保持为空
    }

    // 获取列表
    handleSearch()
})
</script>
