<script>
import {listExportFiles} from './lib/pdf-export-history'
function cleanupExports(){try{listExportFiles()}catch{}}
export default {onShow(){cleanupExports();clearInterval(this.exportCleanup);this.exportCleanup=setInterval(cleanupExports,30000)},onHide(){clearInterval(this.exportCleanup)}}
</script>
<style>
@import "./styles/pdf-ui.css";
/* #ifdef H5 */
::-webkit-scrollbar {
  width: 0;
  height: 0;
}
/* #endif */
page {
  background: #f5faff;
  color: #172642;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
  font-size: 28rpx;
}
view,
text,
button,
textarea {
  box-sizing: border-box;
}
button::after {
  border: none;
}
button {
  font-size: 28rpx;
  line-height: 1.5;
}
.page {
  max-width: 960rpx;
  margin: auto;
  padding: 30rpx 32rpx 56rpx;
}
.title {
  font-size: 40rpx;
  font-weight: 750;
}
.subtitle {
  color: #8291ac;
  font-size: 24rpx;
  margin-top: 10rpx;
  line-height: 1.6;
}
.section-title {
  font-size: 30rpx;
  font-weight: 700;
  margin: 40rpx 0 22rpx;
}
.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 30rpx;
  box-shadow: 0 6rpx 24rpx #204c8810;
}
.primary {
  background: #087bff;
  color: white;
  border-radius: 18rpx;
  padding: 24rpx;
  font-weight: 650;
  margin-top: 28rpx;
}
.primary[disabled] {
  background: #cdbfff;
  color: white;
}
.secondary {
  background: #eaf4ff;
  color: #087bff;
  border-radius: 18rpx;
  padding: 22rpx;
  margin-top: 24rpx;
}
.muted {
  font-size: 24rpx;
  color: #8191a9;
  line-height: 1.7;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
}
.notice {
  background: #eaf4ff;
  color: #6280a9;
  padding: 24rpx;
  border-radius: 18rpx;
  margin-top: 24rpx;
  font-size: 24rpx;
  line-height: 1.8;
}
.empty {
  text-align: center;
  padding: 80rpx 30rpx;
}
.empty-icon {
  font-size: 64rpx;
  color: #9fbbdf;
  margin-bottom: 20rpx;
}
</style>
