<script>
export default {
  onLaunch() {
    console.log('App Launch')
  },
}
</script>

<style>
@import '@/styles/theme.css';

page {
  background-color: var(--mp-bg);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 28rpx;
  color: var(--mp-text-main);
}

button {
  border-radius: 10rpx;
}

button::after {
  border: none;
}

/* ===== 全局通用类: 各页面统一引用, 保证两端风格一致 ===== */

/* 页面渐变底 (顶部一抹红晕过渡到暖纸灰) */
.mp-page-bg {
  background:
    radial-gradient(circle at 14% 0%, rgba(157, 34, 53, 0.10), transparent 34%),
    linear-gradient(180deg, #FBF7F5 0%, var(--mp-bg) 38%, var(--mp-bg) 100%);
}

/* 深红 hero 横幅 */
.mp-hero {
  position: relative;
  border-radius: var(--mp-radius-lg);
  background:
    radial-gradient(circle at 88% -24%, rgba(255, 255, 255, 0.14), transparent 46%),
    var(--mp-red-gradient);
  box-shadow: var(--mp-shadow-red);
  overflow: hidden;
}

/* hero 中的校徽水印 */
.mp-hero-seal {
  position: absolute;
  right: -40rpx;
  bottom: -70rpx;
  opacity: 0.08;
  pointer-events: none;
}

/* 白色信息卡 */
.mp-card {
  background: var(--mp-card);
  border: 1rpx solid rgba(35, 31, 32, 0.06);
  border-radius: 22rpx;
  box-shadow: var(--mp-shadow-card);
}

/* 鎏金小章 (eyebrow) */
.mp-eyebrow {
  display: inline-flex;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  color: rgba(255, 255, 255, 0.88);
  font-size: 21rpx;
  letter-spacing: 1rpx;
}
</style>
