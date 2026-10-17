import {ref} from 'vue'
import {pdfRequest} from './pdf-api'
export const bannerVip=ref(false)
let pending
export function refreshBannerVip(){
 if(!pending)pending=pdfRequest('/quota').then(state=>{bannerVip.value=state.membership?.active===true}).catch(()=>{bannerVip.value=false}).finally(()=>{pending=null})
 return pending
}
