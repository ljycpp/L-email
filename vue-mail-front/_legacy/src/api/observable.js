import { Observable } from 'rxjs/Observable';

export function fromPromise(promise) {
  return Observable.create(observer => {
    promise.then(res => {
      observer.next(res);
      if (observer.complete) {
        observer.complete();
      }
    }).catch(err => observer.error(err));
  });
}
