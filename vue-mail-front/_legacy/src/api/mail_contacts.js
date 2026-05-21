import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList(query) {
    return fetch({
        url: '/mail_contacts/list',
        method: 'get',
        params: query
    });
}

export function add(contactsDTO) {
    return fromPromise(fetch({
        url: '/api/contacts',
        method: 'post',
        data: contactsDTO
    }));
}

export function edit(contactsDTO) {
    return fromPromise(fetch({
        url: '/api/contacts/' + contactsDTO.id,
        method: 'put',
        data: contactsDTO
    }));
}

export function del(idArr) {
    return fromPromise(fetch({
        url: '/api/contacts',
        method: 'delete',
        data: { ids: idArr }
    }));
}
