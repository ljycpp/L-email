<template>
    <div class="app-container calendar-list-container">
    
        <div class="filter-container">
            <el-button v-waves @click="reply()" type="primary" class="tool-item filter-item btn-reply">
                <icon-svg icon-class="reply"/>
            </el-button>
            <el-button v-waves @click="reply(true)" type="primary" class="tool-item filter-item btn-reply-all">
                 <icon-svg icon-class="reply-all"/>
            </el-button>
            <el-button v-waves @click="forward" type="primary" icon="share" class="tool-item filter-item btn-forward"></el-button>
            <el-button v-waves type="danger" icon="delete" class="tool-item filter-item btn-del" v-on:click="handleDelete()"></el-button>
            <el-button v-waves type="primary" class="tool-item filter-item btn-reload" v-on:click="initPage">
                 <icon-svg icon-class="reload4"/>
            </el-button>
            <el-dropdown @command="handleMark" split-button type="primary" menu-align="start" class="tool-item filter-item">
                标记为
                <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item command="star">
                         <icon-svg icon-class="favourite" class="download-icon" />星标邮件</el-dropdown-item>
                    <el-dropdown-item v-for="label in labelList" :key="label.id" :command="label.id + ''">
                         <icon-svg icon-class="label1" class="download-icon" />{{label.name}}</el-dropdown-item>
                </el-dropdown-menu>
            </el-dropdown>
            <el-input @keyup.enter.native="handleFilter" style="width: 300px;" class="filter-item" placeholder="标题" v-model="listQuery.title">
            </el-input>
            <el-select clearable style="width: 120px" class="filter-item" v-model="listQuery.status" placeholder="状态">
                <el-option v-for="status in statusOptions" :key="status.value" :label="status.showValue" :value="status.value">
                </el-option>
            </el-select>
            <el-button class="filter-item" type="primary" v-waves icon="search" @click="handleFilter">搜索</el-button>
            <el-button class="filter-item" type="text" icon="document" @click="handleDownload">导出</el-button>
        </div>
    
        <el-table :key='tableKey' :data="list" ref="multipleTable" @sort-change="customSort" @selection-change="handleSelectionChange" v-loading.body="listLoading" border highlight-current-row style="width: 100%">
            <el-table-column type="selection" min-width="30px"></el-table-column>
            <el-table-column align="left" width="90px" label="信息">
                <template scope="scope">
                    <icon-svg @click.native="toggleStar(scope.row)" :icon-class="scope.row.isStar? 'favourite':'favourite-o'" class="star"/>
                    <icon-svg v-if="scope.row.isHaveFile" icon-class="label4" class="file" />
                    <icon-svg v-if="scope.row.isHaveAudio" icon-class="voice4"/>
                </template>
            </el-table-column>
            <el-table-column prop="status" sortable="custom" class-name="status-col" label="状态" width="80px">
                <template scope="scope">
                    <el-tag :type="scope.row.status | statusTypeFilter">{{scope.row.status | statusShowFilter}}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="sendName" sortable="custom" align="center" label="发件人">
                <template scope="scope">
                    <el-tooltip class="item" effect="dark" :content="scope.row.sendMail" placement="top">
                        <span>{{scope.row.sendName}}</span>
                    </el-tooltip>
                </template>
            </el-table-column>
            <el-table-column prop="title" sortable="custom" label="主题" :show-overflow-tooltip="true" min-width="400px">
                <template scope="scope">
                    <span class="link-type" @click="goToDetail(scope.row.id)">{{scope.row.title}}</span>
                    <el-tag v-for="label in scope.row.labelList" :key="label.guid">{{label.name}}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="receiveDate" sortable="custom" align="center" label="接收时间" width="150px">
                <template scope="scope">
                    <span>{{scope.row.receiveDate | parseTime('{y}-{m}-{d} {h}:{i}')}}</span>
                </template>
            </el-table-column>
            <el-table-column prop="readDate" sortable="custom" align="center" label="阅读时间" width="150px">
                <template scope="scope">
                    <span>{{scope.row.readDate | parseTime('{y}-{m}-{d} {h}:{i}')}}</span>
                </template>
            </el-table-column>
        </el-table>
    
        <div v-show="!listLoading" class="pagination-container">
            <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange" :current-page.sync="listQuery.page" :page-sizes="[10,20,30, 50]" :page-size="listQuery.limit" layout="total, sizes, prev, pager, next, jumper" :total="total">
            </el-pagination>
        </div>
    </div>
</template>

<script>
import * as inboxAPI from 'api/inbox';
import mailListPage from 'mixins/mailListPage';

export default {
    name: 'inbox',
    mixins: [mailListPage],
    data() {
        return {
            listQuery: {
                page: 1,
                limit: 20,
                title: undefined,
                status: undefined,
                sort: '',
                order: ''
            },
            statusOptions: [
                { value: 0, showValue: '未读' },
                { value: 1, showValue: '已读' }
            ]
        };
    },
    filters: {
        statusTypeFilter(status) {
            return status === 1 ? 'primary' : 'danger';
        },
        statusShowFilter(status) {
            return status === 1 ? '已读' : '未读';
        }
    },
    methods: {
        getList() {
            this.listLoading = true;
            inboxAPI.fetchList(this.listQuery).then(response => {
                this.list = response.data.items;
                this.total = response.data.total;
                this.listLoading = false;
            });
        },
        goToDetail(id) {
            this.$store.commit('SET_MAIL_ID', id);
            this.$store.commit('SET_MAIL_TYPE', 'receive');
            this.$router.push({ path: '/mail_detail/index' });
        },
        reply(isAll) {
            this.navigateCompose(isAll ? 'replyAll' : 'reply', 'receive');
        },
        forward() {
            this.navigateCompose('forward', 'receive');
        },
        handleDelete() {
            this.confirmDelete(inboxAPI.delReceiveMail, {
                emptyMessage: '请选择邮件进行删除',
                confirmMessage: '是否删除这{count}封邮件?'
            });
        },
        handleDownload() {
            this.exportExcel(
                ['发件人', '发件邮箱', '主题', '接收时间', '阅读时间'],
                ['sendName', 'sendMail', 'title', 'receiveDate', 'readDate'],
                '收件箱数据'
            );
        }
    }
};
</script>
