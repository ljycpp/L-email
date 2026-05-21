import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList(query) {
    return fetch({
        url: '/outbox/list',
        method: 'get',
        params: query
    });
}

export function delSendMail(idArr) {
    return fromPromise(fetch({
        url: '/mail/delete',
        method: 'post',
        data: { ids: idArr }
    }));
}
