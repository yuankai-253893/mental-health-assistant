<template>
    <el-dialog
        :title="isEdit ? '编辑文章' : '新增文章'"
        v-model="dialogVisible"
        width="50%"
        @close="handleClose"
    >
        <el-form :model="formData" :rules="rules" ref="formRef" label-width="120px">
            <el-form-item label="文章标题" prop="title">
                <el-input v-model="formData.title" placeholder="请输入文章标题" maxlength="200" show-word-limit clearable />
            </el-form-item>
            <el-form-item label="所属分类" prop="categoryId">
                <el-select v-model="formData.categoryId" placeholder="请选择分类">
                    <el-option v-for="item in props.categories" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item label="文章摘要" prop="summary">
                <el-input type="textarea" v-model="formData.summary" placeholder="请输入文章摘要(可选)" maxlength="1000" show-word-limit :rows="4" />
            </el-form-item>
            <el-form-item label="标签" prop="tags">
                <el-select v-model="formData.tagArray" placeholder="请输入文章标签(逗号分隔)" multiple filterable allow-create style="width: 100%">
                    <el-option v-for="tag in commonTags" :key="tag" :label="tag" :value="tag" />
                </el-select>
            </el-form-item>
            <el-form-item label="封面图片">
                <div class="cover-upload">
                    <el-upload
                        class="avatar-uploader"
                        action="#"
                        :before-upload="beforeUpload"
                        :http-request="handleUploadRequest"
                        :show-file-list="false"
                        accept="image/*"
                    >
                        <div v-if="!imgUrl" class="cover-placeholder">
                            <p>点击上传封面</p>
                        </div>
                        <img v-else :src="imgUrl" class="cover-image" alt="封面图片" />
                    </el-upload>
                    <div v-if="imgUrl" class="cover-remove">
                        <el-button type="danger" size="small" @click="handleRemove">移除封面</el-button>
                    </div>
                </div>
            </el-form-item>
            <el-form-item label="文章内容" prop="content">
                <RichTextEditor
                    v-model="formData.content"
                    placeholder="请输入文章内容，支持富文本格式\n\n可以使用加粗、斜体、列表、标题等格式来丰富文章内容。"
                    :maxCharCount="5000"
                    @change="handleContentChange"
                    @created="handleEditorCreated"
                    min-height="400px"
                    />
            </el-form-item>
        </el-form>
        <div v-if="btnPreview">
            <h3>内容预览</h3>
            <div v-html="formData.content"></div>
        </div>
        <template #footer>
            <el-button @click="btnPreview = !btnPreview">{{ btnPreview ? '隐藏预览' : '预览效果' }}</el-button>
            <el-button @click="handleClose">取消</el-button>
            <el-button type="primary" @click="handleSubmit" :loading="loading">{{ isEdit ? '更新文章' : '创建文章' }}</el-button>
        </template>
    </el-dialog>
</template>
<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile, deleteFile } from '@/api/admin'
import { createArticle, updateArticle } from '@/api/knowledge'
import { fileBaseUrl } from '@/config/index.js'
import RichTextEditor from '@/components/backend/RichTextEditor.vue'

const props = defineProps({
    modelValue: {
        type: Boolean,
        default: false
    },
    categories: {
        type: Array,
        default: () => []
    },
    article: {
        type: Object,
        default: null
    }
})

const emit = defineEmits(['update:modelValue', 'success'])

// 对话框显示状态
const dialogVisible = computed({
    get() {
        return props.modelValue
    },
    set(val) {
        emit('update:modelValue', val)
    }
})

const isEdit = computed(() => !!props.article?.id)

// 监听编辑数据
watch(() => props.article, (newVal) => {
    if (newVal) {
        nextTick(() => {
            Object.assign(formData, newVal)
            // 标签在表单里用数组承载（多选 + 允许自由输入），
            // 后端返回的是逗号分隔字符串，需拆分回填，否则编辑保存会把原有标签清空
            formData.tagArray = newVal.tags ? newVal.tags.split(',').filter(t => t) : []
            // 详情接口返回的封面字段名是 cover，需映射回表单的 coverImage，
            // 否则编辑时既不回显封面，保存时还会把已有封面清空
            formData.coverImage = newVal.cover || ''
            // 使用现有ID
            businessId.value = newVal.id
            // 封面Url（无封面时保持空串，避免拼出 "http://localhost:8080undefined" 显示裂图）
            imgUrl.value = formData.coverImage ? fileBaseUrl + formData.coverImage : ''
        })
    }
})

const handleClose = () => {
    // 先清理「本次编辑会话里上传、但从未保存」的封面，再重置表单
    // （必须在 resetFields 之前调用，否则 formData.coverImage 已被清空，无从判断）
    cleanupUnsavedCover()
    // 重置表单
    formRef.value.resetFields()
    // 重置ID
    businessId.value = null
    // 重置标签
    formData.tagArray = []
    // 重置封面图片和数据
    handleRemove()
    emit('update:modelValue', false)
}

// 表单数据
const formData = reactive({
    "title": "",
    "content": "",
    "coverImage": "",
    "categoryId": "",
    "summary": "",
    "tags": "",
    "tagArray": [],
    "id": ""
})

const rules = reactive({
    title: [
        { required: true, message: '请输入文章标题', trigger: 'blur' },
        { max: 200, message: '文章标题最多200个字符', trigger: 'blur' }
    ],
    categoryId: [
        { required: true, message: '请选择分类', trigger: 'change' }
    ],
    content: [
        { required: true, message: '请输入文章内容', trigger: 'blur' },
        { max: 5000, message: '文章内容最多5000个字符', trigger: 'blur' }
    ],
})

const commonTags = [
  '情绪管理', '焦虑', '抑郁', '压力', '睡眠', 
  '冥想', '正念', '放松', '心理健康', '自我成长',
  '人际关系', '工作压力', '学习方法', '生活技巧'
]

// 上传
const imgUrl = ref('')
const beforeUpload = (file) => {
    // 针对上传的文件进行校验
    const isImage = file.type.startsWith('image/')
    const isLt5M = file.size / 1024 / 1024 < 5
    if (!isImage) {
        ElMessage.error('上传封面图片，请选择图片文件')
        return false
    }
    if (!isLt5M) {
        ElMessage.error('上传封面图片，图片大小不能超过5MB')
        return false
    }
    return true
}
const businessId = ref(null)

/**
 * 清理「本次编辑会话里上传、但从未保存」的封面文件，避免磁盘上堆积孤儿图片。
 *
 * 判定规则：当前封面既非空、又不等于文章已保存的封面（props.article.cover），
 * 说明它是本次操作新传的、还没落库，可以安全删除。
 * 绝不删 props.article.cover —— 那是线上正在引用的封面，只能由后端在
 * 「保存文章（换封面）」或「删除文章」时清理；否则用户传了新图再点取消，
 * 就会把线上封面删掉，文章直接裂图。
 */
const cleanupUnsavedCover = async () => {
    const current = formData.coverImage
    if (!current || current === props.article?.cover) return
    try {
        await deleteFile(current)
    } catch (e) {
        // 清理失败只会留下一个孤儿文件，不影响用户操作，无需打扰用户
    }
}

const handleUploadRequest = async ({ file }) => {
    // UUID生成
    businessId.value = crypto.randomUUID()
    try {
        // 后端 Result<String> 的 data 就是文件访问路径字符串本身
        const filePath = await uploadFile(file, {
            businessType: 'ARTICLE',
            businessId: businessId.value,
            businessField: 'cover'
        })

        // 新封面已经拿到，此时旧的「未保存上传件」确定不再被引用，可以安全清理
        await cleanupUnsavedCover()

        // 拼接完整的图片地址
        imgUrl.value = fileBaseUrl + filePath
        formData.coverImage = filePath
    } catch (e) {
        // 上传失败已由响应拦截器统一提示
    }
}

const handleRemove = () => {
    // 只清未保存的上传件；已保存的封面交给后端在保存/删除文章时清理
    cleanupUnsavedCover()
    imgUrl.value = ''
    formData.coverImage = ''
}

// 富文本
const handleContentChange = (data) => {
    formData.content = data.html
}

const editorInstance = ref(null)
const handleEditorCreated = (editor) => {
    editorInstance.value = editor
    // 编辑
    if (formData.content && editor) {
        nextTick(() => {
            editor.setHtml(formData.content)
        })
    }
}

const btnPreview = ref(false)

// 提交
const formRef = ref()
const loading = ref(false)
const handleSubmit = () => {
    formRef.value.validate((valid) => {
        // 校验不通过时直接返回，绝不能继续发起请求
        if (!valid) return
        loading.value = true
        const submitData = {
            ...formData,
            tags: formData.tagArray.join(',')
        }
        delete submitData.tagArray

        if (!isEdit.value) {
            submitData.id = businessId.value
            createArticle(submitData).then(() => {
                loading.value = false
                emit('success')
            }).catch(() => {
                // 失败已由拦截器提示，需手动结束 loading
                loading.value = false
            })
        } else {
            updateArticle(props.article.id, submitData).then(() => {
                loading.value = false
                emit('success')
            }).catch(() => {
                // 失败已由拦截器提示，需手动结束 loading
                loading.value = false
            })
        }
    })
}
</script>
<style lang="scss" scoped>
.cover-placeholder {
    width: 200px;
    height: 120px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #8b949e;
    background: #f6f8fa;
}
.cover-image {
    width: 200px;
    height: 120px;
    display: block;
}
</style>