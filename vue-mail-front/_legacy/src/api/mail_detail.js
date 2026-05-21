import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchDetail(query) {
    return fetch({
        url: '/mail_detail',
        method: 'get',
        params: query
    });
}

export function delMail(id) {
    return fromPromise(fetch({
        url: '/mail/delete',
        method: 'post',
        data: { ids: [id] }
    }));
}
