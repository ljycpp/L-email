import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList(query) {
    return fetch({
        url: '/draftbox/list',
        method: 'get',
        params: query
    });
}

export function delDraft(idArr) {
    return fromPromise(fetch({
        url: '/mail/delete',
        method: 'post',
        data: { ids: idArr }
    }));
}
