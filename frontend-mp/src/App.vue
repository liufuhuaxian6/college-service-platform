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
</style>

