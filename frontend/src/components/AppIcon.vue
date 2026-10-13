<template>
  <view :class="['icon-tile', color, { small }]" :style="{ width: dimension, height: dimension }"
    ><image :src="'/static/icons/' + name + '.png'" mode="aspectFit"
  /></view>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({
  name: { type: String, default: 'document' },
  color: { type: String, default: 'purple' },
  size: { type: Number, default: 92 },
  small: Boolean,
})
const dimension = computed(() => props.size + 'rpx')
</script>
<style scoped>
.icon-tile {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 25%;
  box-shadow:
    inset 0 2rpx 8rpx #ffffff55,
    0 5rpx 12rpx #7c52c918;
}
.icon-tile image {
  width: 62%;
  height: 62%;
}
.red {
  background: linear-gradient(140deg, #ff979c, #ff4b62);
}
.blue {
  background: linear-gradient(140deg, #6ab6ff, #237aff);
}
.orange {
  background: linear-gradient(140deg, #ffc778, #ff980d);
}
.purple {
  background: linear-gradient(140deg, #ad90ff, #743bff);
}
.violet {
  background: linear-gradient(140deg, #c7b3ff, #9565ff);
}
.green {
  background: linear-gradient(140deg, #9bdd76, #4caf36);
}
.indigo {
  background: linear-gradient(140deg, #b0baff, #6f82e8);
}
.pink {
  background: linear-gradient(140deg, #ff9fd7, #ec45b4);
}
.gray {
  background: linear-gradient(140deg, #d0cfdc, #9a98b1);
}
.small {
  border-radius: 18%;
}
</style>
