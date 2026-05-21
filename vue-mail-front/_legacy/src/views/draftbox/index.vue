<template>
    <div class="app-container calendar-list-container">
        <div class="filter-container">
            <el-button v-waves type="danger" icon="delete" class="tool-item filter-item btn-del" v-on:click="handleDelete()"></el-button>
            <el-button v-waves type="primary" class="tool-item filter-item btn-reload" v-on:click="initPage">
                <icon-svg icon-class="reload4" />
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
            <el-input @keyup.enter.native="handleFilter" style="width: 300px;" class="filter-item" placeholder="标题" v-model="listQuery.title"></el-input>
            <el-date-picker v-model="createDateRange" style="width: 200px;" type="datetimerange" :picker-options="dateOptions" placeholder="创建时间" align="right" class="tool-item filter-item"></el-date-picker>
            <el-date-picker v-model="lastModifyDateRange" style="width: 200px;" type="datetimerange" :picker-options="dateOptions" placeholder="最后修改时间" align="right" class="tool-item filter-item"></el-date-picker>
            <el-button class="filter-item" type="primary" v-waves icon="search" @click="handleFilter">搜索</el-button>
            <el-button class="filter-item" type="text" icon="document" @click="handleDownload">导出</el-button>
        </div>

        <el-table :key='tableKey' :data="list" ref="multipleTable" @sort-change="customSort" @selection-change="handleSelectionChange" v-loading.body="listLoading" border highlight-current-row style="width: 100%">
            <el-table-column type="selection" min-width="30px"></el-table-column>
            <el-table-column align="left" width="90px" label="信息">
                <template scope="scope">
                    <icon-svg @click.native="toggleStar(scope.row)" :icon-class="scope.row.isStar? 'favourite':'favourite-o'" class="star" />
                    <icon-svg v-if="scope.row.isHaveFile" icon-class="label4" class="file" />
                    <icon-svg v-if="scope.row.isHaveAudio" icon-class="voice4"/>
                </template>
            </el-table-column>
            <el-table-column align="center" label="收件人" width="120px" :show-overflow-tooltip="true">
                <template scope="scope">
                    <span>{{scope.row.receiveList | showReceiveName}}</span>
                </template>
            </el-table-column>
            <el-table-column prop="title" sortable="custom" label="主题" :show-overflow-tooltip="true" min-width="400px">
                <template scope="scope">
                    <span class="link-type" @click="goToDetail(scope.row.id)">{{scope.row.title}}</span>
                    <el-tag v-for="label in scope.row.labelList" :key="label.guid">{{label.name}}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="createDate" sortable="custom" align="center" label="创建时间" width="150px">
                <template scope="scope">
                    <span>{{scope.row.createDate | parseTime('{y}-{m}-{d} {h}:{i}')}}</span>
                </template>
            </el-table-column>
            <el-table-column prop="lastModifyDate" sortable="custom" align="center" label="最后修改时间" width="150px">
                <template scope="scope">
                    <span>{{scope.row.lastModifyDate | parseTime('{y}-{m}-{d} {h}:{i}')}}</span>
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
import * as draftboxAPI from 'api/draftbox';
import mailListPage from 'mixins/mailListPage';

const dateShortcuts = [{
    text: '最近一周',
    onClick(picker) {
        const end = new Date();
        const start = new Date();
        start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
        picker.$emit('pick', [start, end]);
    }
}, {
    text: '最近一个月',
    onClick(picker) {
        const end = new Date();
        const start = new Date();
        start.setTime(start.getTime() - 3600 * 1000 * 24 * 30);
        picker.$emit('pick', [start, end]);
    }
}, {
    text: '最近三个月',
    onClick(picker) {
        const end = new Date();
        const start = new Date();
        start.setTime(start.getTime() - 3600 * 1000 * 24 * 90);
        picker.$emit('pick', [start, end]);
    }
}];

export default {
    name: 'draftbox',
    mixins: [mailListPage],
    data() {
        return {
            listQuery: {
                page: 1,
                limit: 20,
                title: '',
                sort: '',
                order: ''
            },
            createDateRange: [],
            lastModifyDateRange: [],
            dateOptions: { shortcuts: dateShortcuts }
        };
    },
    filters: {
        showReceiveName(receiveList) {
            let nameStr = '';
            receiveList.forEach(item => {
                nameStr += item.name + ';';
            });
            return nameStr;
        }
    },
    methods: {
        getList() {
            this.listLoading = true;
            this.listQuery.startCreateDate = this.createDateRange[0] ? this.createDateRange[0].getTime() : null;
            this.listQuery.stopCreateDate = this.createDateRange[1] ? this.createDateRange[1].getTime() : null;
            this.listQuery.startModifyDate = this.lastModifyDateRange[0] ? this.lastModifyDateRange[0].getTime() : null;
            this.listQuery.stopModifyDate = this.lastModifyDateRange[1] ? this.lastModifyDateRange[1].getTime() : null;
            draftboxAPI.fetchList(this.listQuery).then(response => {
                this.list = response.data.items;
                this.total = response.data.total;
                this.listLoading = false;
            });
        },
        goToDetail(id) {
            this.$store.commit('SET_DRAFT_ID', id);
            this.$store.commit('SET_MAIL_ID', null);
            this.$store.commit('SET_PAGE_TYPE', 'edit');
            this.$store.commit('SET_MAIL_TYPE', 'draft');
            this.$router.push({ path: '/mail_send/index' });
        },
        handleDelete() {
            this.confirmDelete(draftboxAPI.delDraft, {
                emptyMessage: '请选择草稿进行删除',
                confirmMessage: '是否删除这{count}封草稿?'
            });
        },
        handleDownload() {
            this.exportExcel(
                ['收件人', '主题', '创建时间', '最后修改时间'],
                ['receiveList', 'title', 'createDate', 'lastModifyDate'],
                '草稿箱数据'
            );
        }
    }
};
</script>
