import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

dayjs.locale('zh-cn');

const WEEKDAY = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

export function formatMailDate(value) {
  if (!value) {
    return '';
  }
  const d = dayjs(value);
  const now = dayjs();
  const diffMin = now.diff(d, 'minute');
  if (diffMin < 1) {
    return '刚刚';
  }
  if (diffMin < 60 && d.isSame(now, 'day')) {
    return `${diffMin}分钟前`;
  }
  const diffHour = now.diff(d, 'hour');
  if (diffHour < 24 && d.isSame(now, 'day')) {
    return `${diffHour}小时前`;
  }
  if (d.isSame(now.subtract(1, 'day'), 'day')) {
    return '昨天';
  }
  if (d.isSame(now, 'year')) {
    return d.format('M/D');
  }
  return d.format('YYYY/M/D');
}

function resolveGroupTitle(d, now) {
  if (d.isSame(now, 'day')) {
    return '今天';
  }
  if (d.isSame(now.subtract(1, 'day'), 'day')) {
    return '昨天';
  }
  const daysAgo = now.startOf('day').diff(d.startOf('day'), 'day');
  if (daysAgo >= 2 && daysAgo <= 6) {
    return WEEKDAY[d.day()];
  }
  if (daysAgo >= 7 && daysAgo <= 13) {
    return '上周';
  }
  return '更早';
}

export function groupMailsByDate(items, dateField = 'receiveDate') {
  const bucketMap = new Map();
  const order = [];
  const now = dayjs();

  for (const item of items) {
    const d = dayjs(item[dateField]);
    const title = d.isValid() ? resolveGroupTitle(d, now) : '更早';
    if (!bucketMap.has(title)) {
      bucketMap.set(title, []);
      order.push(title);
    }
    bucketMap.get(title).push(item);
  }

  return order.map(title => ({
    title,
    count: bucketMap.get(title).length,
    items: bucketMap.get(title)
  }));
}
