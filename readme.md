MapView in android to display indoor floor plan

###使用方法
- dependencies 里添加
```
compile 'sysu.mobile.limk:indoormapview:0.1'
```

- 创建一个MapView
```xml
<sysu.mobile.limk.library.MapView
    android:id="@+id/mapview"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    custom:debug="true"
    custom:max_scale="4"
    custom:min_scale="0.2" />
```

- 调用Mapview执行相关操作
```java
// 获取MapView对象
mMapView = (MapView) findViewById(R.id.mapview);
// 初始化MapView
mMapView.initNewMap(getAssets().open("gogo.png"), 1, 0, new Position(652, 684));
```