import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList(query) {
    return fetch({
        url: '/mail_list',
        method: 'get',
        params: query
    });
}

export function delMail(idArr) {
    return fromPromise(fetch({
        url: '/mail/delete',
        method: 'post',
        data: { ids: idArr }
    }));
}

export function unDoDelMail(idArr) {
    return fromPromise(fetch({
        url: '/mail/restore',
        method: 'post',
        data: { ids: idArr }
    }));
}
