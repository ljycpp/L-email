import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function fetchList() {
    return fetch({
        url: '/mail_label/list',
        method: 'get'
    });
}

export function add(labelDTO) {
    return fromPromise(fetch({
        url: '/api/labels',
        method: 'post',
        data: labelDTO
    }));
}

export function edit(labelDTO) {
    return fromPromise(fetch({
        url: '/api/labels/' + labelDTO.id,
        method: 'put',
        data: labelDTO
    }));
}

export function del(id) {
    return fromPromise(fetch({
        url: '/api/labels/' + id,
        method: 'delete'
    }));
}

export function toggleStar(idArr) {
    return fromPromise(fetch({
        url: '/mail_label/toggle_star',
        method: 'post',
        data: { ids: idArr }
    }));
}

export function markLabel(labelId, idArr) {
    return fromPromise(fetch({
        url: '/mail_label/mark',
        method: 'post',
        data: {
            labelId: Number(labelId),
            mailIds: idArr
        }
    }));
}
