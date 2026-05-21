import fetch from 'utils/fetch';
import { fromPromise } from './observable';

export function sendMail(mailDTO) {
    return fromPromise(fetch({
        url: '/mail_send/send',
        method: 'post',
        data: mailDTO
    }));
}

export function saveAsDraft(mailDTO) {
    return fromPromise(fetch({
        url: '/mail_send/draft',
        method: 'post',
        data: mailDTO
    }));
}
