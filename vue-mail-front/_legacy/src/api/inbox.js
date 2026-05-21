import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList(query) {
    return fetch({
        url: '/inbox/list',
        method: 'get',
        params: query
    });
}

export function fetchUnReadList() {
    return fetch({
        url: '/inbox/list',
        method: 'get',
        params: { status: 0, sort: 'receiveDate', order: 'descending' }
    });
}

export function delReceiveMail(idArr) {
    return fromPromise(fetch({
        url: '/mail/delete',
        method: 'post',
        data: { ids: idArr }
    }));
}
