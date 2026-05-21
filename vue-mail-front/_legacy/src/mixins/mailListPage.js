import * as labelAPI from 'api/mail_label';
import { getType } from 'utils/validate';
import { parseTime } from 'utils';

export default {
  data() {
    return {
      list: null,
      total: null,
      listLoading: true,
      listQuery: {
        page: 1,
        limit: 20,
        title: undefined,
        sort: '',
        order: ''
      },
      multipleSelection: [],
      tableKey: 0,
      labelList: []
    };
  },
  created() {
    this.initPage();
  },
  methods: {
    initPage() {
      this.getList();
      this.getLabelList();
    },
    getLabelList() {
      labelAPI.fetchList().then(res => {
        this.labelList = res.data.labelList;
      });
    },
    handleFilter() {
      this.listQuery.page = 1;
      this.getList();
    },
    handleSizeChange(val) {
      this.listQuery.limit = val;
      this.getList();
    },
    handleCurrentChange(val) {
      this.listQuery.page = val;
      this.getList();
    },
    customSort(sortObj) {
      this.listQuery.sort = sortObj.prop;
      this.listQuery.order = sortObj.order;
      this.getList();
    },
    handleSelectionChange(val) {
      this.multipleSelection = val;
    },
    getSelectedIds() {
      return this.multipleSelection.map(item => item.id);
    },
    requireSingleSelection(message) {
      if ((this.multipleSelection.length || 0) !== 1) {
        this.$message(message);
        return null;
      }
      return this.multipleSelection[0];
    },
    confirmDelete(deleteFn, options) {
      const selectedLen = this.multipleSelection.length || 0;
      if (selectedLen < 1) {
        this.$message(options.emptyMessage);
        return;
      }
      this.$confirm(options.confirmMessage.replace('{count}', selectedLen), '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteFn(this.getSelectedIds()).subscribe({
          next: () => {
            this.$message({ message: '删除成功', type: 'success', duration: 2000 });
            this.getList();
          },
          error: () => {
            this.$message({ showClose: true, message: '删除失败', type: 'error' });
          }
        });
      }).catch(() => {
        this.$message('操作已取消');
      });
    },
    exportExcel(tHeader, filterVal, fileName) {
      require.ensure([], () => {
        const { export_json_to_excel } = require('vendor/Export2Excel');
        const data = this.formatJson(filterVal, this.list);
        export_json_to_excel(tHeader, data, parseTime(Date.now()) + fileName);
      });
    },
    formatJson(filterVal, jsonData) {
      return jsonData.map(v => filterVal.map(j => {
        if (~j.indexOf('Date')) {
          return parseTime(v[j]);
        }
        if (getType(v[j]) === 'Array') {
          let str = '';
          v[j].forEach(item => {
            str += item.name + '<' + item.mail + '>;';
          });
          return str;
        }
        return v[j];
      }));
    },
    toggleStar(row) {
      const idArr = row ? [row.id] : this.getSelectedIds();
      if (!row && idArr.length < 1) {
        this.$message('请选择邮件进行标记');
        return;
      }
      labelAPI.toggleStar(idArr).subscribe({
        next: () => {
          if (row) {
            row.isStar = !row.isStar;
          } else {
            this.multipleSelection.forEach(item => {
              item.isStar = true;
            });
          }
        }
      });
    },
    handleMark(labelId) {
      if (labelId === 'star') {
        this.toggleStar();
        return;
      }
      const idArr = this.getSelectedIds();
      if (idArr.length < 1) {
        this.$message('请选择邮件进行标记');
        return;
      }
      labelAPI.markLabel(labelId, idArr).subscribe({
        next: () => this.getList()
      });
    },
    navigateCompose(pageType, mailType) {
      const mail = this.requireSingleSelection('请选择一封邮件');
      if (!mail) {
        return;
      }
      this.$store.commit('SET_MAIL_ID', mail.id);
      this.$store.commit('SET_PAGE_TYPE', pageType);
      this.$store.commit('SET_MAIL_TYPE', mailType);
      this.$router.push({ path: '/mail_send/index' });
    }
  }
};
