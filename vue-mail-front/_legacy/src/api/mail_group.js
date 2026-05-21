import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList() {
    return fetch({
        url: '/mail_group/list',
        method: 'get'
    });
}

export function add(groupDTO) {
    return fromPromise(fetch({
        url: '/api/contact-groups',
        method: 'post',
        data: groupDTO
    }));
}

export function edit(groupDTO) {
    return fromPromise(fetch({
        url: '/api/contact-groups/' + groupDTO.id,
        method: 'put',
        data: groupDTO
    }));
}

export function del(id) {
    return fromPromise(fetch({
        url: '/api/contact-groups/' + id,
        method: 'delete'
    }));
}
