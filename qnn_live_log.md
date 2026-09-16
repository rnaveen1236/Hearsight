09-12 12:12:51.826 I/adbd    (16227): adbd service requested 'shell,v2,TERM=xterm-256color,raw:monkey -p com.hsv2.hsv2_lite 1'
09-12 12:12:52.240 W/Monkey  (25623): args: [-p, com.hsv2.hsv2_lite, 1]
09-12 12:12:52.252 W/Monkey  (25623):  arg: "com.hsv2.hsv2_lite"
09-12 12:12:52.252 W/Monkey  (25623): data="com.hsv2.hsv2_lite"
09-12 12:12:52.278 D/StartingSurfacePreviewController( 4178): checkSupportSurfacePreview app not support, package: com.hsv2.hsv2_lite
09-12 12:12:52.279 I/iopgp   ( 6317): Iopgp: TRANSACTION_IOPGP_ON_INTENT_STARTED sequence_id = 452 pkg_name = com.hsv2.hsv2_lite class_name = com.hsv2.hsv2_lite.MainActivity isCanTrace = 1
09-12 12:12:52.279 D/OplusOsPackageManager( 4178): removePkgFromNotLaunchedList com.hsv2.hsv2_lite
09-12 12:12:52.282 D/WindowManager( 4178): NFW_setClientVisible:ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity clientVisible:true
09-12 12:12:52.282 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.282 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.282 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.282 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.282 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.282 D/FlexibleTaskController( 4178): isSupportFlexibleWindow target = com.hsv2.hsv2_lite/.MainActivity callPkg = FlexibleTaskController support = true ,caller = 4178
09-12 12:12:52.283 I/AppSnapshotController( 4178): init, sysSupport = true, mActivitySupport = false, activityRecord = ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t-1}
09-12 12:12:52.283 E/QosSceneRecognizer[ActivityStartScene]( 4178): notifyActivityStart get launchedFromUid failed through AppManager! launchedFromPackage: com.android.shell, pkg: com.hsv2.hsv2_lite
09-12 12:12:52.283 D/FlexibleTaskController( 4178): isSupportFlexibleWindow target = com.hsv2.hsv2_lite/.MainActivity callPkg = FlexibleTaskController support = true ,caller = 4178
09-12 12:12:52.285 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.285 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.285 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.285 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.285 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.286 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.286 D/ActivityTaskManager( 4178): validateStartingWindowTheme:  Pkg=com.hsv2.hsv2_lite, Translucent=false, Floating=false, ShowWallpaper=false, Disable=false, LaunchSource=4, Prev=null, LaunchedFromPackage=com.android.shell
09-12 12:12:52.286 D/ActivityTaskManager( 4178): addStartingWindow for ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}, mainWin=null, state=INITIALIZING, isKeyguardLocked=false, isKeyguardGoingAway=false
09-12 12:12:52.288 D/TaskFragmentExtImpl( 4178): shouldSetReadyFalse and resumedActivity: ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} prev: ActivityRecord{150777877 u0 com.android.settings/.applications.InstalledAppDetails t1099} prev.isVisible(): true resumedLaunchFromLauncher: false prevLaunchFromApp: false transitionType: 1
09-12 12:12:52.288 I/[UAH_CLIENT]( 4178): UahEventAcquire, cmdid:1, pkg:com.hsv2.hsv2_lite, identity:com.android.server.wm
09-12 12:12:52.289 D/osense.compress( 4178): notifyActivityLaunched pkgName = com.hsv2.hsv2_lite, uid = 10547
09-12 12:12:52.289 I/ActivityTaskManager( 4178): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 cmp=com.hsv2.hsv2_lite/.MainActivity mCallingUid=2000} with LAUNCH_SINGLE_TOP from uid 2000 (BAL_ALLOW_PERMISSION (START_ACTIVITIES_FROM_BACKGROUND permission granted)) result code=0
09-12 12:12:52.290 D/WindowManager( 5958): Add to mViews: android.widget.FrameLayout{53785ba V.E...... ......I. 0,0-0,0 alpha=1.0 viewInfo = },pkg= com.hsv2.hsv2_lite
09-12 12:12:52.291 V/WindowManagerShell( 5958): Transition requested (#5649): android.os.BinderProxy@94c4c61 TransitionRequestInfo { type = OPEN, triggerTask = TaskInfo{userId=0 taskId=1100 effectiveUid=10547 displayId=0 isRunning=true baseIntent=Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 cmp=com.hsv2.hsv2_lite/.MainActivity mCallingUid=2000 } baseActivity=ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} topActivity=ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} origActivity=null realActivity=ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:52.291 V/WindowManagerShell( 5958):  topActivityInfo=ActivityInfo{17bc447 com.hsv2.hsv2_lite.MainActivity} launchCookies=[] positionInParent=Point(0, 0) parentTaskId=-1 isFocused=false isVisible=false isVisibleRequested=false isTopActivityNoDisplay=false isSleeping=false locusId=null displayAreaFeatureId=1 isTopActivityTransparent=false isActivityStackTransparent=false lastNonFullscreenBounds=Rect(274, 673 - 807, 1753) capturedLink=null capturedLinkTimestamp=0 requestedVisibleTypes=-9 topActivityRequestOpenInBrowserEducationTimestamp=0
09-12 12:12:52.292 I/AppSnapshotController( 4178): attachStartingWindow, win = Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite}, shouldAppSnapshot = false
09-12 12:12:52.292 D/ActivityTaskManager( 4178): attachStartingWindow: mStartingData=SplashScreenStartingData{a49cfe8 removeAfterTransaction= 0}, mAssociatedTask=null, mStartingWindow=Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite}
09-12 12:12:52.292 I/WindowManager( 4178): addWindow: New client android.os.BinderProxy@9c8e685 : window=Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite}
09-12 12:12:52.293 I/AppsFilter( 4178): interaction: PackageSetting{210d7f7 com.intel.realsense.capture/10457} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{6fd40cd com.example.d455andriodapp/10452} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{f213d82 com.android.microdroid.empty_payload/10353} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{7a23083 com.example.hsv2realsense/10467} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{6f0d0f5 com.hearsight.hsv2lite/10515} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{ed9e6c4 com.example.hsv2_lite/10514} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{6af29ad com.oplus.locationproxy/10242} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{15a4e2 com.android.certinstaller/10291} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/AppsFilter( 4178): interaction: PackageSetting{a404073 com.oplus.location/10311} -> PackageSetting{403eca6 com.hsv2.hsv2_lite/10547} BLOCKED
09-12 12:12:52.294 I/OplusHansManager ( 4178): front pkg: com.hsv2.hsv2_lite, uid: 10547, prev pkg: com.android.settings, prev uid: 1000
09-12 12:12:52.294 D/RefreshRate( 4178): Trigger app switch boost, ar=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100},currDisplayRate=120.00001,boostId=3
09-12 12:12:52.294 D/RefreshRate( 4178): startAppSwitchBoost targetMode={id=6, width=1080, height=2354, fps=120.00001, vsync=120.00001, synthetic=false, alternativeRefreshRates=[60.000004, 90.0, 144.00002, 165.0], supportedHdrTypes=[1, 2, 3, 4]} record=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}: votes=PRIORITY_START_ANIMATION_BOOST -> Vote{mRefreshRate=120.0, mMinRefreshRate=120.0, mRequestSource=null};PRIORITY_WINDOW_REQUEST -> Vote{mRefreshRate=120.0, mMinRefreshRate=120.0, mRequestSource=null};
09-12 12:12:52.294 I/[UAH_CLIENT]( 4178): UahEventAcquire, cmdid:1, pkg:com.hsv2.hsv2_lite, identity:com.android.server.wm
09-12 12:12:52.295 D/ActivityTaskManager( 4178): setAppVisibility(Token{4bf907 ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}}, visible=true):  visible=false mVisibleRequested=false
09-12 12:12:52.295 D/ActivityTaskManager( 4178): setVisibility(Token{4bf907 ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}}, visible=true) defer commitVisibility for transition collecting
09-12 12:12:52.295 I/[UAH_CLIENT]( 4178): UahEventAcquire, cmdid:1, pkg:com.hsv2.hsv2_lite, identity:com.android.server.wm
09-12 12:12:52.296 I/OPM-Process( 4178): resume pkg: com.hsv2.hsv2_lite, uid:10547, prev pkg: com.android.settings, prev uid: 1000
09-12 12:12:52.297 D/NativeFreezeManager( 4178): freezeForAppSwitchScene mAppLaunchFzDxEnable enter, mFgAppPkgname com.hsv2.hsv2_lite
09-12 12:12:52.298 I/Osense-BaseDecisionMaker( 4178): notifySceneToAthenaPolicy: SCENE_APP_SWITCH, bundle: Bundle[{prevPkgName=com.android.settings, hasActivities=false, curUid=10547, isGame=false, curPkgName=com.hsv2.hsv2_lite, prevUid=1000, procStarted=false}]
09-12 12:12:52.304 D/ViewRootImplExtImpl( 5958): wrapConfigInfoIntoFlags rotation=0, smallestScreenWidthDp=0, isAutolayoutSwdpModified=false, residentWS=false, scenario=0, bounds=Rect(0, 0 - 1080, 2354), isFirstDraw=true, relayoutAsync=false, flags=0, newFlags=-913309696, title=Splash Screen com.hsv2.hsv2_lite
09-12 12:12:52.305 V/WindowManager( 4178): Relayout Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite}: viewVisibility=0, oldvis=0, req=1080x2354 , apr = 8, , x=0, y=0
09-12 12:12:52.305 I/WindowManager( 4178): Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite} state from NO_SURFACE to DRAW_PENDING; reason: resetDrawState
09-12 12:12:52.308 I/.hsv2.hsv2_lite(15131): Late-enabling -Xcheck:jni
09-12 12:12:52.308 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.308 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.308 I/OplusHansManager ( 4178): onUidActive() update stay time, uid=10547, pkgName=com.hsv2.hsv2_lite, U stay=31
09-12 12:12:52.309 I/ActivityManager( 4178): Start proc 15131:com.hsv2.hsv2_lite/u0a547 for next-top-activity {com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:52.309 W/VRI[hsv2_lite]( 5958): updateBlastSurfaceIfNeeded, surfaceSize:Point(1080, 2354), lastSurfaceSize:Point(0, 0), format:-3, blastBufferQueue:null
09-12 12:12:52.309 I/OStatsManager_OplusBatteryStatsManager( 4178): updateStateTime: PkgName = com.hsv2.hsv2_lite, uid = 10547, state to bg, mForegroundCnt = 0, isFg = false
09-12 12:12:52.309 I/MemoryOperationUtils( 4178): add 15131 to com.hsv2.hsv2_lite
09-12 12:12:52.309 D/BufferQueueConsumer( 5958): [VRI[hsv2_lite]#5782(BLAST Consumer)5782](id:17460000175a,api:0,p:-1,c:5958) setMaxAcquiredBufferCount: 2
09-12 12:12:52.310 D/BufferQueueProducer( 5958): [VRI[hsv2_lite]#5782(BLAST Consumer)5782](id:17460000175a,api:1,p:5958,c:5958) connect: api=1 producerControlledByApp=true
09-12 12:12:52.311 I/SurfaceControl( 5958):  setExtendedRangeBrightness sc=Surface(name=Splash Screen com.hsv2.hsv2_lite)/@0xd037510,currentBufferRatio=1.0,desiredRatio=1.0
09-12 12:12:52.312 D/BLASTBufferQueue( 5958): [VRI[hsv2_lite]#5782](f:0,a:1) acquireNextBufferLocked size=1080x2354 mFrameNumber=1 applyTransaction=true mTimestamp=412775327246912(auto) mPendingTransactions.size=0 graphicBufferId=25589415319690 transform=0
09-12 12:12:52.312 D/VRI[hsv2_lite]( 5958): Received frameCommittedCallback lastAttemptedDrawFrameNum=1 didProduceBuffer=true syncBuffer=false
09-12 12:12:52.313 D/VRI[hsv2_lite]( 5958): draw finished. seqId=0
09-12 12:12:52.313 D/WindowManager( 4178): onSyncFinishedDrawing: Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite}
09-12 12:12:52.313 I/WindowManager( 4178): finishDrawing skipLayout:false,syncSeqId=0,mPrepareSyncSeqId=0 Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite} com.android.server.wm.WindowManagerService.finishDrawingWindow:3256 com.android.server.wm.Session.finishDrawing:403 android.view.IWindowSession$Stub.onTransact:807 
09-12 12:12:52.313 I/WindowManager( 4178): Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite} state from DRAW_PENDING to COMMIT_DRAW_PENDING; reason: finishDrawingLocked
09-12 12:12:52.314 I/WindowManager( 4178): Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite} state from COMMIT_DRAW_PENDING to READY_TO_SHOW; reason: commitFinishDrawingLocked
09-12 12:12:52.314 D/ActivityTaskManager( 4178): commitVisibility: ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}: visible=true visibleRequested=true, isInTransition=true 
09-12 12:12:52.314 I/WindowManager( 4178): Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite} state from READY_TO_SHOW to HAS_DRAWN; reason: performShowLocked
09-12 12:12:52.314 W/SQLiteLog(24855): (28) double-quoted string literal: "com.hsv2.hsv2_lite"
09-12 12:12:52.314 I/WindowStateExtImpl( 4178): onSurfaceShowChange show = true mPackageName = com.hsv2.hsv2_lite parentPkgName = com.hsv2.hsv2_lite
09-12 12:12:52.316 D/OplusTransitionAnimationManager( 4178):    ChangeInfo{5b8bbea container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x30}
09-12 12:12:52.316 D/OplusTransitionAnimationManager( 4178):    ChangeInfo{bbfed78 container=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} flags=0x0}
09-12 12:12:52.321 W/VRI[hsv2_lite]( 5958): handleResized, msg:, frameChanged:false, configChanged:false, displayChanged:false, attachedFrameChanged:false, compatScaleChanged:false, pendingDragResizing=false
09-12 12:12:52.322 V/WindowManager( 4178): Sent Transition (#5649) createdAt=09-12 12:12:52.283 via request=TransitionRequestInfo { type = OPEN, triggerTask = TaskInfo{userId=0 taskId=1100 effectiveUid=10547 displayId=0 isRunning=true baseIntent=Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 cmp=com.hsv2.hsv2_lite/.MainActivity mCallingUid=2000 } baseActivity=ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} topActivity=ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} origActivity=null realActivity=ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:52.322 V/WindowManager( 4178):  numActivities=1 lastActiveTime=775824579 supportsSplitScreenMultiWindow=true supportsMultiWindow=true supportsPocketStudioMultiWindow=true isInFlexibleEmbedded = false uid = 10547 pid = 0 resizeMode=1 isResizeable=true minWidth=-1 minHeight=-1 defaultMinSize=220 token=WCT{RemoteToken{94ef390 Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}}} topActivityType=1 pictureInPictureParams=null shouldDockBigOverlays=false launchIntoPipHostTaskId=-1 lastParentTaskIdBeforePip=-1 displayCutoutSafeInsets=Rect(0, 120 - 0, 0)
09-12 12:12:52.322 V/WindowManager( 4178):  topActivityInfo=ActivityInfo{633c489 com.hsv2.hsv2_lite.MainActivity} launchCookies=[] positionInParent=Point(0, 0) parentTaskId=-1 isFocused=false isVisible=false isVisibleRequested=false isTopActivityNoDisplay=false isSleeping=false locusId=null displayAreaFeatureId=1 isTopActivityTransparent=false isActivityStackTransparent=false lastNonFullscreenBounds=Rect(274, 673 - 807, 1753) capturedLink=null capturedLinkTimestamp=0 requestedVisibleTypes=-9 topActivityRequestOpenInBrowserEducationTimestamp=0
09-12 12:12:52.322 V/WindowManager( 4178):  mOplusExtraBundle = Bundle[{androidx.flexible.taskFrame=Rect(0, 0 - 0, 0), androidx.flexible.Alpha=1.0, androidx.activity.task.attach.blur=0, androidx.flexible.task_snapshot_mode=0, androidx.activity.LaunchInsensible=0, androidx.activity.LaunchCornerRadius=0, key_flexible_task_state=0, topActivitySupportAppInnerSplit=false, key_flexible_task_animating_type=-1, key_flexible_task_root_activity_processname=com.hsv2.hsv2_lite, androidx.activity.LaunchScenario=0, is_oplus_clone_task=false, flexible_super_mini_state=false, topRunningActivityHash=145288584, flexible.floatwindow.is_multi_instance=false, androidx.activity.LaunchScale=1.0, androidx.activity.LaunchShadowRadius=0, is_transient_hide_for_task_info=false, androidx.activity.key_flexible_task_pending_exit_to=0, key_flexible_task_root_activity_pid=0, key_flexible_task_root_activity_uid=10547}]}, pipChange = null, remoteTransition = null, displayChange = null, flags = 0, debugId = 5649 }
09-12 12:12:52.322 V/WindowManager( 4178):         {WCT{RemoteToken{94ef390 Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}}} m=OPEN f=MOVE_TO_TOP leash=Surface(name=Task=1100)/@0x9e0148c sb=Rect(0, 0 - 1080, 2354) eb=Rect(0, 0 - 1080, 2354) epz=Point(1080, 2354) d=0 taskParent=-1},
09-12 12:12:52.323 D/CommandQueueLogger( 5958): onSystemBarAttributesChanged displayId=0 appearance=24 packageName=com.hsv2.hsv2_lite
09-12 12:12:52.323 D/FlexibleWindowManagerService( 4178): should not dispatch appeared task Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}
09-12 12:12:52.324 E/.hsv2.hsv2_lite(15131): Unknown bits set in runtime_flags: 0x40000000
09-12 12:12:52.324 I/.hsv2.hsv2_lite(15131): Using generational CollectorTypeCMC GC.
09-12 12:12:52.331 I/ActivityTaskManager( 4178): Process com.hsv2.hsv2_lite restarted
09-12 12:12:52.332 D/SplitScreenAppReader( 4178): SPLIT_TOUCH support check:com.hsv2.hsv2_lite = false
09-12 12:12:52.332 D/ResourcesManagerCache( 4178): For package com.hsv2.hsv2_lite we got cacheList [null] appInfoExt is null ? false hasFeature ? false
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.333 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.334 I/UserAwareMgr( 4178): handleTopAppChanged: com.hsv2.hsv2_lite, pid: 15131, uid: 10547
09-12 12:12:52.334 D/ActivityTaskManager( 4178): onWindowFocusChanged Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} would transfer to compact
09-12 12:12:52.334 D/ActivityTaskManager( 4178): NFW_setLastResumedActivityUncheckLocked:true r:ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} currentFocus:null
09-12 12:12:52.334 D/FlexibleTaskController( 4178): onTaskFocusChanged prevTask:Task{cb6f11b #1099 type=standard A=1000:.applications.InstalledAppDetailsTop} ,currentTask:Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}, reason: realStartActivityLocked - onActivityStateChanged
09-12 12:12:52.334 D/OplusFlexibleDCSManager( 4178): onNextPkgChanged pkg = com.hsv2.hsv2_lite
09-12 12:12:52.335 I/OplusAppSwitchManagerService( 4178): appswitch hasProcess ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}
09-12 12:12:52.335 I/[UAH_CLIENT]( 4178): UahEventAcquire, cmdid:0, pkg:com.hsv2.hsv2_lite, identity:com.android.server.wm
09-12 12:12:52.335 I/GameManagerServiceExt-GameManagerServiceExt( 4178): on FG change: com.android.settings|com.hsv2.hsv2_lite
09-12 12:12:52.335 I/OplusAppSwitchManagerService( 4178): appswitch hasProcess ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}
09-12 12:12:52.335 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.335 D/OplusAppSwitchListener( 4178): Detect com.hsv2.hsv2_lite switch to foreground
09-12 12:12:52.337 D/OplusWifiStateTracker( 4178): notifyUidForeground pkgName=com.hsv2.hsv2_lite uid=10547
09-12 12:12:52.337 E/OplusThermalStats( 4178): Error getting package info: com.hsv2.hsv2_lite
09-12 12:12:52.338 D/VRI[hsv2_lite]( 5958): Received frameCommittedCallback lastAttemptedDrawFrameNum=2 didProduceBuffer=false syncBuffer=false
09-12 12:12:52.338 D/VRI[hsv2_lite]( 5958): draw finished. seqId=0
09-12 12:12:52.341 D/BatteryStatsImplExtImpl( 4178): resumedUid:10547 resumedBatteryLevel:33  resumedPackage:com.hsv2.hsv2_lite  resumedBatteryRealtimeCapacity:1994
09-12 12:12:52.343 D/AudioFlingerExtImpl( 2864): updateForegroundInfo, mapInf mForegroundMap(1)|[com.hsv2.hsv2_lite]=1, uidPidMapInf mForegroundUidPidMap(1)|[10547]=15131
09-12 12:12:52.343 I/OStatsManager_Calc( 4178): noteBeforePkgSwitch change = true, curTime = 775824624, com.hsv2.hsv2_lite, fg = false
09-12 12:12:52.343 I/OStatsManager_OplusBatteryStatsManager( 4178): updateStateTime: PkgName = com.hsv2.hsv2_lite, uid = 10547, state to fg, mForegroundCnt = 1, isFg = true
09-12 12:12:52.343 I/OStatsManager_OplusBatteryStatsManager( 4178): notePkgForeground [com.hsv2.hsv2_lite10547, com.android.settings1000], uid = 10547, pid = 15131, pkgName = com.hsv2.hsv2_lite
09-12 12:12:52.344 V/DCSEX-AppSwitchManager( 5658): OnAppSwitchObserver: onAppExit, resuming = com.hsv2.hsv2_lite, target = com.android.settings, is ResumingFirstStart =  true, is ResumingMultiApp =  false, extensionInfo =  Bundle[mParcelledData.dataSize=764]
09-12 12:12:52.344 W/Battery ( 6981): AppSwitchDetectorV2: OnAppSwitchObserver: com.hsv2.hsv2_lite.MainActivity lastReg=1789180319335
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/DCSEX-AppLaunchEventRecorder( 5658): onAppSwitch from = com.android.settings to = com.hsv2.hsv2_lite
09-12 12:12:52.344 D/OplusLatencyOptimizerManager( 4178): enter pkg: com.hsv2.hsv2_lite isPrevBlackAppTop: false mIsBlackAppTop: false
09-12 12:12:52.344 D/OplusEapManager( 4178): mTargetEnterName :com.hsv2.hsv2_lite
09-12 12:12:52.344 D/VRR [OPlusRefreshRateService]( 4178): handleFrontAppChange pkgName: com.hsv2.hsv2_lite
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 W/Battery ( 6981): ThermalControlUtils: mForegroundPkg=com.hsv2.hsv2_lite mLastWindowMode=1
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.344 I/Athena  ( 6386): AthenaDynamicReceiver: pre_app:com.android.settings, next_app:com.hsv2.hsv2_lite(10547|15131)
09-12 12:12:52.345 V/DCSEX-AppSwitchManager( 5658): OnAppSwitchObserver: onAppEnter, launchedFrom =  com.android.shell, target =  com.hsv2.hsv2_lite, is firstStart =  true, is multiApp =  false, extensionInfo =  Bundle[{activity=com.hsv2.hsv2_lite.MainActivity, isTaskEmbedded=false, launch_source_traceid=, launchScenario=0, isGame=false, taskId=1100, exitActivityPkg=com.android.settings, baseUserId=0, exitBaseUserId=0, exitActivity=com.android.settings.applications.InstalledAppDetailsTop, basePkg=com.hsv2.hsv2_lite, fix=false, pid=15131, uid=10547, label=null, exitTaskId=1099, exitScenario=0, displayid=0, versionCode=1, launch_source_type=, outSideScreen=false, exitBasePkg=com.android.settings, insensibleFlag=0}], launch_source_type =  , launch_source_traceid =  
09-12 12:12:52.345 W/Battery ( 6981): ThermalControllHandler: tempLevel=[2],userMode=2,gameUserMode=7,appType=-1,envTempType=0,appStates=[],appSwitchSafetyMode=true,split=[],float={},full=[com.hsv2.hsv2_lite], tempGear=[2],configItemName=common_config
09-12 12:12:52.346 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195372344, eventType=2, targetName=com.android.settings, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.hsv2.hsv2_lite, resumingActivityName=com.hsv2.hsv2_lite.MainActivity, isResumingMultiApp=false, isResumingFirstStart=true)
09-12 12:12:52.346 I/metis_v2_AppSwitchDataCollector( 6797): AppExitEvent =  com.android.settings, NextAppName = com.hsv2.hsv2_lite
09-12 12:12:52.346 I/Athena  ( 6386): EmMem: handleFgApp pkgName=com.hsv2.hsv2_lite,uid=10547
09-12 12:12:52.347 I/statsd  ( 2481): An anomaly (104240000001) (42)0x10000->10547[I] (42)0x20000->com.hsv2.hsv2_lite[S] (42)0x30000->.MainActivity[S]  has occurred! Informing subscribers.
09-12 12:12:52.347 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195372344, eventType=2, targetName=com.android.settings, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.hsv2.hsv2_lite, resumingActivityName=com.hsv2.hsv2_lite.MainActivity, isResumingMultiApp=false, isResumingFirstStart=true)
09-12 12:12:52.347 I/ContextAware_AppRecommendAdapter( 6797): fenceCallbackForAlg receive app recommend from AppSwitchEvent(eventId=2, switchInfo=AppSwitchInfo(targetName=com.android.settings, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.hsv2.hsv2_lite, resumingActivityName=com.hsv2.hsv2_lite.MainActivity, isResumingFirstStart=true, isResumingMultiApp=false))
09-12 12:12:52.348 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195372346, eventType=1, targetName=com.hsv2.hsv2_lite, firstStart=true, multiApp=false, windowMode=1, resumingPackageName=, resumingActivityName=, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:52.348 I/ContextAware_AppRecommendAdapter( 6797): fenceCallbackForAlg receive app recommend from AppSwitchEvent(eventId=1, switchInfo=AppSwitchInfo(targetName=com.hsv2.hsv2_lite, firstStart=true, multiApp=false, windowMode=1, resumingPackageName=, resumingActivityName=, isResumingFirstStart=false, isResumingMultiApp=false))
09-12 12:12:52.350 I/midasd  ( 7674): app forground called:com.hsv2.hsv2_lite
09-12 12:12:52.351 I/statsd  ( 2481): An anomaly (119840000001) (100198)0x10000->com.hsv2.hsv2_lite[S] (100198)0x20000->10547[I]  has occurred! Informing subscribers.
09-12 12:12:52.352 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195372346, eventType=1, targetName=com.hsv2.hsv2_lite, firstStart=true, multiApp=false, windowMode=1, resumingPackageName=, resumingActivityName=, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:52.352 I/metis_v2_AppSwitchDataCollector( 6797): AppEnterEvent =  com.hsv2.hsv2_lite, windowMode = 1,firstStart = true
09-12 12:12:52.352 W/metis_v2_AppSwitchDataCollector( 6797): fenceKey = com.oplus.metis:TOP_APPLICATION, getExits = [], getEnters = [] packageName = com.hsv2.hsv2_lite
09-12 12:12:52.425 D/nativeloader(15131): Configuring clns-9 for other apk /data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk. target_sdk_version=36, uses_libraries=, library_path=/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/lib/arm64:/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk!/lib/arm64-v8a, permitted_path=/data:/mnt/expand:/data/user/0/com.hsv2.hsv2_lite
09-12 12:12:52.426 I/.hsv2.hsv2_lite(15131): AssetManager2(0xb40000741f0a0a28) locale list changing from [] to [en-IN]
09-12 12:12:52.428 V/GraphicsEnvironment(15131): com.hsv2.hsv2_lite is not listed in per-application setting
09-12 12:12:52.428 V/GraphicsEnvironment(15131): com.hsv2.hsv2_lite is not listed in ANGLE allowlist or settings, returning default
09-12 12:12:52.436 I/.hsv2.hsv2_lite(15131): AssetManager2(0xb4000073daa2c328) locale list changing from [] to [en-IN]
09-12 12:12:52.436 D/vulkan  (15131): searching for layers in '/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/lib/arm64'
09-12 12:12:52.437 D/vulkan  (15131): searching for layers in '/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk!/lib/arm64-v8a'
09-12 12:12:52.439 D/vulkan  (15131): added global layer 'VK_LAYER_KHRONOS_validation' from library '/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk!/lib/arm64-v8a/libVkLayer_khronos_validation.so'
09-12 12:12:52.476 D/nativeloader(15131): Load /data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk!/lib/arm64-v8a/libhsv2_lite.so using class loader ns clns-9 (caller=/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk!classes7.dex): ok
09-12 12:12:52.476 I/HSV2Lite(15131): Native hsv2_lite library loaded
09-12 12:12:52.476 D/OplusScrollManager(15131): hookActivity: ActivityInfo{8781e4f com.hsv2.hsv2_lite.MainActivity} mRTOptEnable false mStartScrollerSpringEnable false
09-12 12:12:52.479 D/OplusPredictiveBackController(15131): should not HookOnBackInvokedCallbackEnabled for application com.hsv2.hsv2_lite.MainActivity@542506b sdk: 36
09-12 12:12:52.479 D/OplusPredictiveBackController(15131): initPredictiveBackConfig for com.hsv2.hsv2_lite.MainActivity@542506b initPredictiveBackConfig mShouldInterceptKeyEvent false
09-12 12:12:52.479 D/WindowOnBackDispatcher(15131):  predictive settings is disabled for ApplicationInfo{f7f07c8 com.hsv2.hsv2_lite}
09-12 12:12:52.480 I/qspmHal ( 2709): gpuProfName:com.hsv2.hsv2_lite-gpu - gpu prof_info.path:/data/vendor/gaming/com.hsv2.hsv2_lite-gpu
09-12 12:12:52.481 I/qspmHal ( 2709): gpuProfName:com.hsv2.hsv2_lite-gpu - gpu prof_info.path:/data/vendor/gaming/com.hsv2.hsv2_lite-gpu
09-12 12:12:52.486 D/nativeloader(15131): Load /data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk!/lib/arm64-v8a/libflutter.so using class loader ns clns-9 (caller=/data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/base.apk): ok
09-12 12:12:52.497 I/AdrenoVK-0(15131): Process Name        : com.hsv2.hsv2_lite
09-12 12:12:52.500 D/PreCache(15131): [IPreCacheRus]loadRUSConfigs#102 failed to transact: -22 prc:com.hsv2.hsv2_lite
09-12 12:12:52.655 I/.hsv2.hsv2_lite(15131): AssetManager2(0xb4000073dfcdf028) locale list changing from [] to [en-IN]
09-12 12:12:52.665 I/qspmHal ( 2709): gpuProfName:com.hsv2.hsv2_lite-gpu - gpu prof_info.path:/data/vendor/gaming/com.hsv2.hsv2_lite-gpu
09-12 12:12:52.666 I/AdrenoVK-0(15131): Process Name        : com.hsv2.hsv2_lite
09-12 12:12:52.763 I/HSV2Lite(15131): Copying QNN context binary from assets...
09-12 12:12:52.813 I/HSV2Lite(15131): QNN context binary copied: /data/user/0/com.hsv2.hsv2_lite/files/qnn/yolov8s_int8_sm8850.bin.SM8850.bin
09-12 12:12:52.813 I/HSV2Lite(15131): QNN model library path: /data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/lib/arm64/libyolov8s_int8.so
09-12 12:12:52.813 I/HSV2Lite(15131): QNN context path: /data/user/0/com.hsv2.hsv2_lite/files/qnn/yolov8s_int8_sm8850.bin.SM8850.bin
09-12 12:12:52.813 I/HSV2Lite(15131): QNN model path:   /data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/lib/arm64/libyolov8s_int8.so
09-12 12:12:52.813 E/HSV2Lite(15131): QNN model library does not exist: /data/app/~~0zLZA6VRdvuYVX2rYG-WIQ==/com.hsv2.hsv2_lite-YEfBrgCbfd0w_Xe1q0FOPQ==/lib/arm64/libyolov8s_int8.so
09-12 12:12:52.821 I/.hsv2.hsv2_lite(15131): hiddenapi: Accessing hidden method Landroid/view/accessibility/AccessibilityNodeInfo;->getSourceNodeId()J (runtime_flags=0, domain=platform, api=unsupported,test-api) from Lio/flutter/view/AccessibilityViewEmbedder$ReflectionAccessors; (domain=app, TargetSdkVersion=36) using reflection: allowed
09-12 12:12:52.821 I/.hsv2.hsv2_lite(15131): hiddenapi: Accessing hidden method Landroid/view/accessibility/AccessibilityRecord;->getSourceNodeId()J (runtime_flags=0, domain=platform, api=unsupported) from Lio/flutter/view/AccessibilityViewEmbedder$ReflectionAccessors; (domain=app, TargetSdkVersion=36) using reflection: allowed
09-12 12:12:52.821 I/.hsv2.hsv2_lite(15131): hiddenapi: Accessing hidden field Landroid/view/accessibility/AccessibilityNodeInfo;->mChildNodeIds:Landroid/util/LongArray; (runtime_flags=0, domain=platform, api=unsupported) from Lio/flutter/view/AccessibilityViewEmbedder$ReflectionAccessors; (domain=app, TargetSdkVersion=36) using reflection: allowed
09-12 12:12:52.821 I/.hsv2.hsv2_lite(15131): hiddenapi: Accessing hidden method Landroid/util/LongArray;->get(I)J (runtime_flags=0, domain=platform, api=unsupported) from Lio/flutter/view/AccessibilityViewEmbedder$ReflectionAccessors; (domain=app, TargetSdkVersion=36) using reflection: allowed
09-12 12:12:52.830 D/ActivityThread(15131): ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} checkFinished=false 2
09-12 12:12:52.832 D/WindowManager(15131): Add to mViews: com.android.internal.policy.DecorView{9dc129c I.E...... R.....ID 0,0-0,0 alpha=1.0 viewInfo = }[MainActivity],pkg= com.hsv2.hsv2_lite
09-12 12:12:52.835 I/WindowManager( 4178): addWindow: New client android.os.BinderProxy@21496b0 : window=Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:52.836 D/OplusScrollManager(15131): hookActivity: ActivityInfo{8781e4f com.hsv2.hsv2_lite.MainActivity} mRTOptEnable false mStartScrollerSpringEnable false
09-12 12:12:52.854 D/Transition( 4178): finishTransition ar ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}, isVisibleRequested=true, state=RESUMED, visibleAtTransitionEndTokens={ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}}, this=TransitionRecord{af397d5 id=5649 track=0 type=OPEN flags=0x0 state=2 c=[
09-12 12:12:52.854 D/Transition( 4178):    ChangeInfo{5b8bbea container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x30}
09-12 12:12:52.854 D/Transition( 4178):    ChangeInfo{bbfed78 container=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} flags=0x0}
09-12 12:12:52.854 D/Transition( 4178): finishTransition ar ActivityRecord{150777877 u0 com.android.settings/.applications.InstalledAppDetails t1099}, isVisibleRequested=false, state=PAUSED, visibleAtTransitionEndTokens={ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}}, this=TransitionRecord{af397d5 id=5649 track=0 type=OPEN flags=0x0 state=2 c=[
09-12 12:12:52.854 D/Transition( 4178):    ChangeInfo{5b8bbea container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x30}
09-12 12:12:52.854 D/Transition( 4178):    ChangeInfo{bbfed78 container=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} flags=0x0}
09-12 12:12:52.854 I/OplusAppSwitchManagerService( 4178):  handleAppVisible , r = ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}
09-12 12:12:52.854 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.854 D/OplusTransitionController( 4178):    ChangeInfo{5b8bbea container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x30}
09-12 12:12:52.854 D/OplusTransitionController( 4178):    ChangeInfo{bbfed78 container=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} flags=0x0}
09-12 12:12:52.855 D/FlexibleTaskTransitionController( 4178):    ChangeInfo{5b8bbea container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x30}
09-12 12:12:52.855 D/FlexibleTaskTransitionController( 4178):    ChangeInfo{bbfed78 container=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} flags=0x0}
09-12 12:12:52.856 D/RefreshRate( 4178):     | COURSE[; Win preferred rateId=0, appReqFirst=false, data= RateData{} ;win name:Splash Screen com.hsv2.hsv2_lite]
09-12 12:12:52.878 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:52.939 D/VRR [OPlusGameStatusManager]( 4178): updateGameAccelerationStatus pkgName: com.hsv2.hsv2_lite mOSyncFeatureSwitch: false mIsGamePolicy: false mHeartStop: true mHasFlickerRisk: false mForceAcceleration: false mToastReady: false enable: false
09-12 12:12:53.118 I/DynamicFramerate [AnimationInfoManager](15131): updatePixelSize: sPixelSize has been updated, process = com.hsv2.hsv2_lite
09-12 12:12:53.126 D/WindowOnBackDispatcher(15131):  predictive settings is disabled for ApplicationInfo{f7f07c8 com.hsv2.hsv2_lite}
09-12 12:12:53.126 D/WindowOnBackDispatcher(15131):  predictive settings is disabled for ApplicationInfo{f7f07c8 com.hsv2.hsv2_lite}
09-12 12:12:53.126 D/CoreBackPreview( 4178): Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}: Setting back callback OnBackInvokedCallbackInfo{mCallback=android.window.IOnBackInvokedCallback$Stub$Proxy@fca76f8, mPriority=0, mIsAnimationCallback=false, mOverrideBehavior=0}
09-12 12:12:53.127 D/ViewRootImplExtImpl(15131): wrapConfigInfoIntoFlags rotation=0, smallestScreenWidthDp=0, isAutolayoutSwdpModified=false, residentWS=false, scenario=0, bounds=Rect(0, 0 - 1080, 2354), isFirstDraw=true, relayoutAsync=false, flags=0, newFlags=-913309696, title=com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity
09-12 12:12:53.127 V/WindowManager( 4178): Relayout Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}: viewVisibility=0, oldvis=4, req=1080x2354 , apr = 0, , x=0, y=0
09-12 12:12:53.127 I/WindowManager( 4178): Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} state from NO_SURFACE to DRAW_PENDING; reason: resetDrawState
09-12 12:12:53.128 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} mCurrentFocus:null
09-12 12:12:53.128 V/WindowManager( 4178): Changing focus from null to Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity},diplayid=0
09-12 12:12:53.129 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.129 D/CommandQueueLogger( 5958): onSystemBarAttributesChanged displayId=0 appearance=528 packageName=com.hsv2.hsv2_lite
09-12 12:12:53.134 I/SurfaceFlinger( 2878): [SF client] NEW(0xb4000077b20dc700) for (15131:com.hsv2.hsv2_lite)
09-12 12:12:53.135 D/SurfaceComposerClient( 4178): Transaction::apply InputWindowCommands.focusRequests timestamp=412776144801756, windowName=4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity
09-12 12:12:53.135 D/SurfaceView(15131): 160239356, SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity], layerid: 86339 createBlastSurfaceControls
09-12 12:12:53.135 D/BufferQueueConsumer(15131): [98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1(BLAST Consumer)1](id:3b1b00000001,api:0,p:-1,c:15131) setMaxAcquiredBufferCount: 2
09-12 12:12:53.135 I/.hsv2.hsv2_lite(15131): Compiler allocated 7307KB to compile void android.view.ViewRootImpl.performTraversals()
09-12 12:12:53.136 D/BufferQueueProducer(15131): [98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1(BLAST Consumer)1](id:3b1b00000001,api:1,p:15131,c:15131) connect: api=1 producerControlledByApp=true
09-12 12:12:53.136 D/BLASTBufferQueue(15131): 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1 getBufferCacheFlag 0
09-12 12:12:53.136 D/BLASTBufferQueue(15131): 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1 getMaxAcquireBufferCount 2
09-12 12:12:53.136 D/BLASTBufferQueue(15131): 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1 getOrignalMaxAcquireBufferCount 3
09-12 12:12:53.136 D/BLASTBufferQueue(15131): 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1 setMaxAcquireBufferCount 3
09-12 12:12:53.136 D/BufferQueueConsumer(15131): [98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1(BLAST Consumer)1](id:3b1b00000001,api:1,p:15131,c:15131) setMaxAcquiredBufferCount: 3
09-12 12:12:53.136 D/BLASTBufferQueue(15131): 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1 setBufferCacheFlag 1
09-12 12:12:53.136 D/BLASTBufferQueue(15131): 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1 getBufferCacheFlag 1
09-12 12:12:53.146 D/OplusStartingWindowManager( 4178): updateRecordSurfaceViewState windowToken = android.os.BinderProxy@21496b0, surfaceState = true, attachedToWindow = true, inList = false, win = Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:53.147 D/SurfaceFlinger( 2878): updateWinowInfo=1, setFocusedWindow timestamp=412776144801756, windowName=4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity
09-12 12:12:53.147 I/InputDispatcher( 4178): NFW_setFocusedWindow, 4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity on display 0, same as the previous:0
09-12 12:12:53.150 I/GameManagerServiceExt-GameMiniProgramManagerService( 4178): updateTaskLabel: com.hsv2.hsv2_lite, , com.hsv2.hsv2_lite.MainActivity, uid: 10547, pid: 15131, label: HSV2 Lite, taskId: 1100, mTaskID: 0
09-12 12:12:53.152 I/HSV2Lite(15131): Found Intel RealSense device
09-12 12:12:53.152 I/HSV2Lite(15131): Requesting USB permission
09-12 12:12:53.157 D/TaskFragmentExtImpl( 4178): shouldSetReadyFalse and resumedActivity: ActivityRecord{72315979 u0 com.android.systemui/.usb.UsbPermissionActivity t1101} prev: ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} prev.isVisible(): true resumedLaunchFromLauncher: false prevLaunchFromApp: true transitionType: 1
09-12 12:12:53.192 I/AdrenoGLES-0(15131): Process Name                     : com.hsv2.hsv2_lite
09-12 12:12:53.194 I/qspmHal ( 2709): gpuProfName:com.hsv2.hsv2_lite-gpu - gpu prof_info.path:/data/vendor/gaming/com.hsv2.hsv2_lite-gpu
09-12 12:12:53.199 I/HSV2Native(15131): ANativeWindow set successfully!
09-12 12:12:53.201 I/HSV2Native(15131): ANativeWindow for Depth set successfully!
09-12 12:12:53.204 I/DynamicFramerate [FRTCConfigManager](15131): FRTCConfigManager: FRTC_CAPABILITY = 0, package name = com.hsv2.hsv2_lite, WINDOW_ANIMATION_SPEED_RATE = 10, SCROLL_ANIMATION_SPEED_RATE = 20, PACKAGE_ENABLE = true
09-12 12:12:53.204 I/DynamicFramerate [DynamicFrameRateController](15131): init info: mPackageName = com.hsv2.hsv2_lite, mIsEnabled = true
09-12 12:12:53.207 I/OplusHansManager ( 4178): front pkg: com.android.systemui, uid: 10281, prev pkg: com.hsv2.hsv2_lite, prev uid: 10547
09-12 12:12:53.207 I/OplusHansManager ( 4178): uid=10547, pkg=com.hsv2.hsv2_lite enter SM
09-12 12:12:53.207 I/OplusHansManager ( 4178): uid=10547, pkg=com.hsv2.hsv2_lite R enter(), D stay=0
09-12 12:12:53.207 I/Osense-BaseDecisionMaker( 4178): notifySceneToAthenaPolicy: SCENE_APP_SWITCH, bundle: Bundle[{prevPkgName=com.hsv2.hsv2_lite, hasActivities=false, curUid=10281, isGame=false, curPkgName=com.android.systemui, prevUid=10547, procStarted=true}]
09-12 12:12:53.207 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.208 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.208 I/OPM-Process( 4178): resume pkg: com.android.systemui, uid:10281, prev pkg: com.hsv2.hsv2_lite, prev uid: 10547
09-12 12:12:53.208 D/ActivityTaskManager( 4178): NFW_setLastResumedActivityUncheckLocked:true r:ActivityRecord{72315979 u0 com.android.systemui/.usb.UsbPermissionActivity t1101} currentFocus:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:53.208 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:null mCurrentFocus:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:53.208 V/WindowManager( 4178): Changing focus from Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} to null,diplayid=0
09-12 12:12:53.209 D/FlexibleTaskController( 4178): onTaskFocusChanged prevTask:Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} ,currentTask:Task{1dde741 #1101 type=standard A=10281:com.android.systemui}, reason: realStartActivityLocked - onActivityStateChanged
09-12 12:12:53.209 I/GameManagerServiceExt-GameManagerServiceExt( 4178): on FG change: com.hsv2.hsv2_lite|com.android.systemui
09-12 12:12:53.209 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.210 D/OplusAppSwitchListener( 4178): Detect com.hsv2.hsv2_lite switch to background
09-12 12:12:53.210 D/OplusFeatureReduceBrightness( 4178): onAppExit , info = com.hsv2.hsv2_lite
09-12 12:12:53.210 D/OplusEapManager( 4178): mTargetExitName :com.hsv2.hsv2_lite
09-12 12:12:53.210 V/DCSEX-AppSwitchManager( 5658): OnAppSwitchObserver: onAppExit, resuming = com.android.systemui, target = com.hsv2.hsv2_lite, is ResumingFirstStart =  false, is ResumingMultiApp =  false, extensionInfo =  Bundle[mParcelledData.dataSize=736]
09-12 12:12:53.211 I/Athena  ( 6386): AthenaDynamicReceiver: pre_app:com.hsv2.hsv2_lite, next_app:com.android.systemui(10281|5958)
09-12 12:12:53.211 D/DCSEX-AppLaunchEventRecorder( 5658): onAppSwitch from = com.hsv2.hsv2_lite to = com.android.systemui
09-12 12:12:53.211 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195373210, eventType=2, targetName=com.hsv2.hsv2_lite, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.android.systemui, resumingActivityName=com.android.systemui.usb.UsbPermissionActivity, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:53.211 I/ContextAware_AppRecommendAdapter( 6797): fenceCallbackForAlg receive app recommend from AppSwitchEvent(eventId=2, switchInfo=AppSwitchInfo(targetName=com.hsv2.hsv2_lite, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.android.systemui, resumingActivityName=com.android.systemui.usb.UsbPermissionActivity, isResumingFirstStart=false, isResumingMultiApp=false))
09-12 12:12:53.211 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195373210, eventType=2, targetName=com.hsv2.hsv2_lite, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.android.systemui, resumingActivityName=com.android.systemui.usb.UsbPermissionActivity, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:53.211 I/metis_v2_AppSwitchDataCollector( 6797): AppExitEvent =  com.hsv2.hsv2_lite, NextAppName = com.android.systemui
09-12 12:12:53.211 I/metis_v2_AppSwitchDataCollector( 6797): Ignore app exit!  Next App: com.android.systemui, Exit App: com.hsv2.hsv2_lite
09-12 12:12:53.211 I/Athena  ( 6386): EmMem: handleBgApp pkgName=com.hsv2.hsv2_lite,uid=10547
09-12 12:12:53.211 V/DCSEX-AppSwitchManager( 5658): OnAppSwitchObserver: onAppEnter, launchedFrom =  android, target =  com.android.systemui, is firstStart =  false, is multiApp =  false, extensionInfo =  Bundle[{activity=com.android.systemui.usb.UsbPermissionActivity, isTaskEmbedded=false, launch_source_traceid=, launchScenario=0, isGame=false, taskId=1101, exitActivityPkg=com.hsv2.hsv2_lite, baseUserId=0, exitBaseUserId=0, exitActivity=com.hsv2.hsv2_lite.MainActivity, basePkg=com.android.systemui, fix=false, pid=5958, uid=10281, label=null, exitTaskId=1100, exitScenario=0, displayid=0, versionCode=169912, launch_source_type=, outSideScreen=false, exitBasePkg=com.hsv2.hsv2_lite, insensibleFlag=0}], launch_source_type =  , launch_source_traceid =  
09-12 12:12:53.214 D/WindowLayoutComponentImpl(15131): app addWindowLayoutInfoListener activity:com.hsv2.hsv2_lite.MainActivity@542506b consumer :androidx.window.layout.adapter.extensions.MulticastConsumer@113a6df
09-12 12:12:53.214 D/WindowLayoutComponentImpl(15131): Register WindowLayoutInfoListener on Context=com.hsv2.hsv2_lite.MainActivity@542506b, of which baseContext=android.app.ContextImpl@1b02f2c
09-12 12:12:53.230 D/CommandQueueLogger( 5958): onSystemBarAttributesChanged displayId=0 appearance=24 packageName=com.hsv2.hsv2_lite
09-12 12:12:53.237 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.245 D/BLASTBufferQueue(15131): [98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]#1](f:0,a:1) acquireNextBufferLocked size=1080x2354 mFrameNumber=1 applyTransaction=true mTimestamp=412776247683058(auto) mPendingTransactions.size=0 graphicBufferId=64987150155786 transform=0
09-12 12:12:53.245 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.247 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.249 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.255 I/OStatsManager_Calc( 4178): add screen power pkgName = com.hsv2.hsv2_lite, uid = 10547, power = 142.17239999771118, is Fg = true, fore size = 2, mScale = 1.0
09-12 12:12:53.256 I/OStatsManager_OplusBatteryStatsManager( 4178): notePkgBackground [com.hsv2.hsv2_lite10547], uid = 1000, pid = 5575, pkgName = com.android.settings
09-12 12:12:53.272 I/SurfaceControl(15131):  setExtendedRangeBrightness sc=Surface(name=com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity)/@0xae737c4,currentBufferRatio=1.0,desiredRatio=1.0
09-12 12:12:53.281 I/WindowManager( 4178): Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} state from DRAW_PENDING to COMMIT_DRAW_PENDING; reason: finishDrawingLocked
09-12 12:12:53.282 I/WindowManager( 4178): Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} state from COMMIT_DRAW_PENDING to READY_TO_SHOW; reason: commitFinishDrawingLocked
09-12 12:12:53.282 D/ActivityTaskManager( 4178): Finish StartingWindow: ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}first real window is shown
09-12 12:12:53.282 D/ActivityTaskManager( 4178): removeStartingWindowAnimation starting ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} startingWindow=null ,startingView=com.android.server.wm.StartingSurfaceController$StartingSurface@d25124a,Callers=com.android.server.wm.ActivityRecord.removeStartingWindow:3414 com.android.server.wm.ActivityRecord.onFirstWindowDrawn:8106 com.android.server.wm.WindowState.performShowLocked:4982 com.android.server.wm.WindowStateAnimator.commitFinishDrawingLocked:403 com.android.server.wm.DisplayContent.lambda$new$8:1152 
09-12 12:12:53.282 D/OplusStartingWindowManager( 4178): interceptRemoveStartingWindow: packageName=com.hsv2.hsv2_lite,  componentName=com.hsv2.hsv2_lite/.MainActivity, isSnapshot=false, startingSurfaceRunnable=null, isDrawCompleted=false, hasSurfaceView=false, inTransition=false
09-12 12:12:53.282 D/OplusSplitScreenDnDUI( 4178): notifyRemoveStartingWindow isSpitScreenDrag = false mDropTargetSurface = null StartingWindowpkgName = com.hsv2.hsv2_lite DragPkg = null isLauncherResume = false
09-12 12:12:53.282 D/OplusStartingWindowManager( 4178): interceptRemoveStartingWindow: hasSplashWindowFlag() return false for activity=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}
09-12 12:12:53.282 D/TaskOrganizerController( 4178): TaskOrganizerController.removeStartingWindow: call wmshell to removeStartingWindow Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}null
09-12 12:12:53.283 D/QuickBackInputPolicy( 4178): setAppLaunchEndTime update timePair=Pair{ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} 412776297}
09-12 12:12:53.283 I/WindowManager( 4178): Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} state from READY_TO_SHOW to HAS_DRAWN; reason: performShowLocked
09-12 12:12:53.283 I/ActivityTaskManager( 4178): Displayed com.hsv2.hsv2_lite/.MainActivity for user 0: +1s4ms
09-12 12:12:53.284 I/statsd  ( 2481): An anomaly (104840000001) (48)0x10000->10547[I] (48)0x20000->com.hsv2.hsv2_lite[S] (48)0x30000->3[I]  has occurred! Informing subscribers.
09-12 12:12:53.506 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.637 D/BufferQueueProducer( 5958): [VRI[hsv2_lite]#5782(BLAST Consumer)5782](id:17460000175a,api:1,p:5958,c:5958) disconnect: api 1
09-12 12:12:53.638 D/ViewRootImplExtImpl( 5958): wrapConfigInfoIntoFlags rotation=0, smallestScreenWidthDp=360, isAutolayoutSwdpModified=false, residentWS=false, scenario=0, bounds=Rect(0, 0 - 1080, 2354), isFirstDraw=false, relayoutAsync=false, flags=0, newFlags=1234185472, title=Splash Screen com.hsv2.hsv2_lite
09-12 12:12:53.638 V/WindowManager( 4178): Relayout Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite}: viewVisibility=8, oldvis=0, req=1080x2354 , apr = 8, 
09-12 12:12:53.640 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:53.644 D/BLASTBufferQueue( 5958): [VRI[hsv2_lite]#5782](f:0,a:1) destructor()
09-12 12:12:53.644 D/SurfaceAnimationRunner( 4178): onAnimationStart:Surface(name=Surface(name=a4dcda Splash Screen com.hsv2.hsv2_lite)/@0x9d80c25 - animation-leash of window_animation)/@0xe4ea1fa
09-12 12:12:53.644 D/BufferQueueConsumer( 5958): [VRI[hsv2_lite]#5782(BLAST Consumer)5782](id:17460000175a,api:0,p:-1,c:5958) disconnect
09-12 12:12:53.791 D/SurfaceAnimationRunner( 4178): onAnimationEnd:Surface(name=Surface(name=a4dcda Splash Screen com.hsv2.hsv2_lite)/@0x9d80c25 - animation-leash of window_animation)/@0xe4ea1fa
09-12 12:12:53.791 I/WindowStateExtImpl( 4178): onSurfaceShowChange show = false mPackageName = com.hsv2.hsv2_lite parentPkgName = com.hsv2.hsv2_lite
09-12 12:12:53.792 I/WindowManager( 4178): Window{a4dcda u0 Splash Screen com.hsv2.hsv2_lite EXITING} state from HAS_DRAWN to NO_SURFACE; reason: destroySurface
09-12 12:12:53.808 D/ActivityAdapter( 6439): notifyActivityQuery: [com.hsv2.hsv2_lite; com.hsv2.hsv2_lite.MainActivity; 1; COMMON; 10547; 0; 0]
09-12 12:12:53.808 D/OPlusFrontAppManager( 6439): useAdapterResult, pkg=com.hsv2.hsv2_lite,activity=com.hsv2.hsv2_lite.MainActivity,result=true
09-12 12:12:53.808 D/OPlusFrontAppManager( 6439): decide -->> [com.hsv2.hsv2_lite; com.hsv2.hsv2_lite.MainActivity; 1; COMMON; 10547; 0; 0]
09-12 12:12:53.808 D/ConferenceScene( 6439): pkg:com.hsv2.hsv2_lite, activity:com.hsv2.hsv2_lite.MainActivity, appSense:COMMON
09-12 12:12:53.808 D/ExtremeCommService( 6439): updateAppInfo appInfoUnit: com.hsv2.hsv2_lite, winMode:1
09-12 12:12:53.808 D/FrontAppsEvent( 6439): onAppSceneUpdate, infoList:[{pkgName:com.hsv2.hsv2_lite,activityName:,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.809 D/SceneService( 6439): notifyAppSceneChanged original mode:{live:0,video:0,voice call:0,video call:0,live host:0,game:0,meeting:0,news:0,file transfer:0},appInfoUnit:[{pkgName:com.hsv2.hsv2_lite,activityName:,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.809 D/Subsys-ScoreAppManager( 6439): appSceneChange fgAppUnits size 2 fgAppUnits [{pkgName:com.hsv2.hsv2_lite,activityName:,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}, {pkgName:com.oplus.aiunit,activityName:,winMode:1,event:FILE_TRANSFER,type:0,subEvent:0,pid:0,uid:10220, hostPkgName:}] mLastNetworkList[351]
09-12 12:12:53.809 D/EstManager( 6439): onAppSceneChanged, pkgName:com.hsv2.hsv2_lite, type:0,event:0
09-12 12:12:53.809 D/AppSenseClient( 4178): handleSenseEvent appInfoUnits: {pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:},
09-12 12:12:53.809 D/AppSenseClient( 4178): notifyAppEvent: pkgName: com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity;state: 1;event: COMMON;type: 0;subEvent: 0;pid: 15131;uid: 10547
09-12 12:12:53.809 D/AppSenseClient( 4178): notifyAppChanges: [{pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.809 D/AppSenseClient( 4178): notifyAppEvent: pkgName: com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity;state: 1;event: COMMON;type: 0;subEvent: 0;pid: 15131;uid: 10547
09-12 12:12:53.809 D/AppSenseClient( 4178): notifyAppChanges: [{pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.809 D/AppSenseClient( 6221): handleSenseEvent appInfoUnits: {pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:},
09-12 12:12:53.809 D/AppSenseClient( 6221): notifyAppEvent: pkgName: com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity;state: 1;event: COMMON;type: 0;subEvent: 0;pid: 15131;uid: 10547
09-12 12:12:53.809 D/AppSenseClient( 6221): notifyAppChanges: [{pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.809 D/NhsAppSenseListener( 6221): new event: event=0, state=1, pkg=com.hsv2.hsv2_lite, type=0, subEvent=0
09-12 12:12:53.809 D/AppSenseService( 6439): notifySenseEvent: {pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:},
09-12 12:12:53.810 D/AppSenseClient( 6207): handleSenseEvent appInfoUnits: {pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:},
09-12 12:12:53.810 D/AppSenseClient( 6207): notifyAppEvent: pkgName: com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity;state: 1;event: COMMON;type: 0;subEvent: 0;pid: 15131;uid: 10547
09-12 12:12:53.810 D/AppSenseClient( 6207): notifyAppChanges: [{pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.810 D/AppSenseClient( 6153): handleSenseEvent appInfoUnits: {pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:},
09-12 12:12:53.810 D/NAS-DataSceneEvent( 6207): new event: event=0, state=1, pkg=com.hsv2.hsv2_lite, type=0, subEvent=0
09-12 12:12:53.810 D/AppSenseClient( 6153): notifyAppEvent: pkgName: com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity;state: 1;event: COMMON;type: 0;subEvent: 0;pid: 15131;uid: 10547
09-12 12:12:53.810 D/NAS-DataSceneEvent( 6207): updateAppSenseChange [{pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.810 D/AppSenseClient( 6153): notifyAppChanges: [{pkgName:com.hsv2.hsv2_lite,activityName:com.hsv2.hsv2_lite.MainActivity,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}]
09-12 12:12:53.811 D/Subsys-ScoreAppManager( 6439): unit: {pkgName:com.hsv2.hsv2_lite,activityName:,winMode:1,event:COMMON,type:0,subEvent:0,pid:15131,uid:10547, hostPkgName:}
09-12 12:12:54.214 W/Battery ( 6981): Thermal.FpsControl: writeFps 0 com.hsv2.hsv2_lite
09-12 12:12:55.510 D/TaskFragmentExtImpl( 4178): shouldSetReadyFalse and resumedActivity: ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} prev: ActivityRecord{72315979 u0 com.android.systemui/.usb.UsbPermissionActivity t1101 f}} prev.isVisible(): true resumedLaunchFromLauncher: false prevLaunchFromApp: false transitionType: 2
09-12 12:12:55.510 D/TaskFragmentExtImpl( 4178): shouldSetReadyFalse and resumedActivity is already visible: ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}
09-12 12:12:55.514 I/OplusHansManager ( 4178): front pkg: com.hsv2.hsv2_lite, uid: 10547, prev pkg: com.android.systemui, prev uid: 10281
09-12 12:12:55.514 I/HSV2Lite(15131): USB Permission granted for device: /dev/bus/usb/002/002
09-12 12:12:55.514 D/RefreshRate( 4178): Trigger app switch boost, ar=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100},currDisplayRate=120.00001,boostId=3
09-12 12:12:55.514 D/RefreshRate( 4178): startAppSwitchBoost targetMode={id=6, width=1080, height=2354, fps=120.00001, vsync=120.00001, synthetic=false, alternativeRefreshRates=[60.000004, 90.0, 144.00002, 165.0], supportedHdrTypes=[1, 2, 3, 4]} record=ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100}: votes=PRIORITY_START_ANIMATION_BOOST -> Vote{mRefreshRate=120.0, mMinRefreshRate=120.0, mRequestSource=null};PRIORITY_WINDOW_REQUEST -> Vote{mRefreshRate=120.0, mMinRefreshRate=120.0, mRequestSource=null};
09-12 12:12:55.515 I/UserAwareMgr( 4178): handleTopAppChanged: com.hsv2.hsv2_lite, pid: 15131, uid: 10547
09-12 12:12:55.515 D/ActivityTaskManager( 4178): onWindowFocusChanged Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} would transfer to compact
09-12 12:12:55.515 I/OplusHansManager ( 4178): uid=10547, pkg=com.hsv2.hsv2_lite R exit SM
09-12 12:12:55.515 D/ActivityTaskManager( 4178): NFW_setLastResumedActivityUncheckLocked:true r:ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} currentFocus:Window{5335a82 u0 com.android.systemui/com.android.systemui.usb.UsbPermissionActivity}
09-12 12:12:55.515 I/OplusHansManager ( 4178): uid=10547, pkg=com.hsv2.hsv2_lite R exit(), R stay=2
09-12 12:12:55.515 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} mCurrentFocus:Window{5335a82 u0 com.android.systemui/com.android.systemui.usb.UsbPermissionActivity}
09-12 12:12:55.515 D/NativeFreezeManager( 4178): freezeForAppSwitchScene mAppLaunchFzDxEnable enter, mFgAppPkgname com.hsv2.hsv2_lite
09-12 12:12:55.515 V/WindowManager( 4178): Changing focus from Window{5335a82 u0 com.android.systemui/com.android.systemui.usb.UsbPermissionActivity} to Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity},diplayid=0
09-12 12:12:55.515 I/HSV2Lite(15131): USB Device opened successfully. FD: 203, Path: /dev/bus/usb/002/002
09-12 12:12:55.515 I/HSV2Native(15131): Init RealSense with FD: 203, Path: /dev/bus/usb/002/002
09-12 12:12:55.515 I/OPM-Process( 4178): resume pkg: com.hsv2.hsv2_lite, uid:10547, prev pkg: com.android.systemui, prev uid: 10281
09-12 12:12:55.515 I/Osense-BaseDecisionMaker( 4178): notifySceneToAthenaPolicy: SCENE_APP_SWITCH, bundle: Bundle[{prevPkgName=com.android.systemui, hasActivities=true, curUid=10547, isGame=false, curPkgName=com.hsv2.hsv2_lite, prevUid=10281, procStarted=true}]
09-12 12:12:55.516 I/HSV2Native(15131): librealsense2 context instantiated successfully!
09-12 12:12:55.516 D/FlexibleTaskController( 4178): onTaskFocusChanged prevTask:Task{1dde741 #1101 type=standard A=10281:com.android.systemui} ,currentTask:Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}, reason: resumeTopActivity - onActivityStateChanged
09-12 12:12:55.516 D/OplusFlexibleDCSManager( 4178): onNextPkgChanged pkg = com.hsv2.hsv2_lite
09-12 12:12:55.516 I/[UAH_CLIENT]( 4178): UahEventAcquire, cmdid:0, pkg:com.hsv2.hsv2_lite, identity:com.android.server.wm
09-12 12:12:55.516 I/GameManagerServiceExt-GameManagerServiceExt( 4178): on FG change: com.android.systemui|com.hsv2.hsv2_lite
09-12 12:12:55.516 I/OplusAppSwitchManagerService( 4178): com.hsv2.hsv2_lite notifyGcSupression , firstStart = false
09-12 12:12:55.516 I/HSV2Native(15131): Successfully injected USB FD into rsusb backend!
09-12 12:12:55.516 D/OplusAppSwitchListener( 4178): Detect com.hsv2.hsv2_lite switch to foreground
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.517 D/ActivityRecordExtImpl( 4178):  setIsWaitingForAppResume for ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} isWaiting true
09-12 12:12:55.517 V/DCSEX-AppSwitchManager( 5658): OnAppSwitchObserver: onAppExit, resuming = com.hsv2.hsv2_lite, target = com.android.systemui, is ResumingFirstStart =  false, is ResumingMultiApp =  false, extensionInfo =  Bundle[mParcelledData.dataSize=744]
09-12 12:12:55.517 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.518 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.518 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.518 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.518 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.518 D/OplusLatencyOptimizerManager( 4178): enter pkg: com.hsv2.hsv2_lite isPrevBlackAppTop: false mIsBlackAppTop: false
09-12 12:12:55.518 D/OplusEapManager( 4178): mTargetEnterName :com.hsv2.hsv2_lite
09-12 12:12:55.518 D/ActivityThread(15131): ComponentInfo{com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} checkFinished=false 4
09-12 12:12:55.518 W/Battery ( 6981): AppSwitchDetectorV2: OnAppSwitchObserver: com.hsv2.hsv2_lite.MainActivity lastReg=1789180319335
09-12 12:12:55.519 W/Battery ( 6981): ThermalControlUtils: mForegroundPkg=com.hsv2.hsv2_lite mLastWindowMode=1
09-12 12:12:55.519 W/Battery ( 6981): ThermalControllHandler: tempLevel=[2],userMode=2,gameUserMode=7,appType=-1,envTempType=0,appStates=[],appSwitchSafetyMode=true,split=[],float={},full=[com.hsv2.hsv2_lite], tempGear=[2],configItemName=common_config
09-12 12:12:55.519 D/DCSEX-AppLaunchEventRecorder( 5658): onAppSwitch from = com.android.systemui to = com.hsv2.hsv2_lite
09-12 12:12:55.519 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.519 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.519 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.519 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.519 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.519 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.519 D/VRR [OPlusRefreshRateService]( 4178): handleFrontAppChange pkgName: com.hsv2.hsv2_lite
09-12 12:12:55.520 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.520 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.520 D/OplusScrollManager(15131): hookActivity: ActivityInfo{8781e4f com.hsv2.hsv2_lite.MainActivity} mRTOptEnable false mStartScrollerSpringEnable false
09-12 12:12:55.520 D/OplusTransitionAnimationManager( 4178):    ChangeInfo{3704da4 container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x20}
09-12 12:12:55.520 V/DCSEX-AppSwitchManager( 5658): OnAppSwitchObserver: onAppEnter, launchedFrom =  com.android.shell, target =  com.hsv2.hsv2_lite, is firstStart =  false, is multiApp =  false, extensionInfo =  Bundle[{activity=com.hsv2.hsv2_lite.MainActivity, isTaskEmbedded=false, launch_source_traceid=, launchScenario=0, isGame=false, taskId=1100, exitActivityPkg=com.android.systemui, baseUserId=0, exitBaseUserId=0, exitActivity=com.android.systemui.usb.UsbPermissionActivity, basePkg=com.hsv2.hsv2_lite, fix=false, pid=15131, uid=10547, label=HSV2 Lite, exitTaskId=1101, exitScenario=0, displayid=0, versionCode=1, launch_source_type=, outSideScreen=false, exitBasePkg=com.android.systemui, insensibleFlag=0}], launch_source_type =  , launch_source_traceid =  
09-12 12:12:55.520 I/Athena  ( 6386): AthenaDynamicReceiver: pre_app:com.android.systemui, next_app:com.hsv2.hsv2_lite(10547|15131)
09-12 12:12:55.521 I/Athena  ( 6386): EmMem: handleFgApp pkgName=com.hsv2.hsv2_lite,uid=10547
09-12 12:12:55.522 E/OplusThermalStats( 4178): Error getting package info: com.hsv2.hsv2_lite
09-12 12:12:55.523 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195375521, eventType=2, targetName=com.android.systemui, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.hsv2.hsv2_lite, resumingActivityName=com.hsv2.hsv2_lite.MainActivity, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:55.523 I/metis_v2_AppSwitchDataCollector( 6797): AppExitEvent =  com.android.systemui, NextAppName = com.hsv2.hsv2_lite
09-12 12:12:55.523 I/metis_v2_AppSwitchDataCollector( 6797): Ignore app exit!  Next App: com.hsv2.hsv2_lite, Exit App: com.android.systemui
09-12 12:12:55.523 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195375521, eventType=1, targetName=com.hsv2.hsv2_lite, firstStart=false, multiApp=false, windowMode=1, resumingPackageName=, resumingActivityName=, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:55.523 I/metis_v2_AppSwitchDataCollector( 6797): AppEnterEvent =  com.hsv2.hsv2_lite, windowMode = 1,firstStart = false
09-12 12:12:55.523 D/ViewRootImplExtImpl(15131): wrapConfigInfoIntoFlags rotation=0, smallestScreenWidthDp=360, isAutolayoutSwdpModified=false, residentWS=false, scenario=0, bounds=Rect(0, 0 - 1080, 2354), isFirstDraw=false, relayoutAsync=true, flags=0, newFlags=1234185472, title=com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity
09-12 12:12:55.523 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195375521, eventType=2, targetName=com.android.systemui, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.hsv2.hsv2_lite, resumingActivityName=com.hsv2.hsv2_lite.MainActivity, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:55.523 I/ContextAware_AppRecommendAdapter( 6797): fenceCallbackForAlg receive app recommend from AppSwitchEvent(eventId=2, switchInfo=AppSwitchInfo(targetName=com.android.systemui, firstStart=false, multiApp=false, windowMode=0, resumingPackageName=com.hsv2.hsv2_lite, resumingActivityName=com.hsv2.hsv2_lite.MainActivity, isResumingFirstStart=false, isResumingMultiApp=false))
09-12 12:12:55.523 D/SceneSDK.1.0.4-SNAPSHOT.6797.DeepThinkerAppSwitchAbility( 6797): onAppSwitchEvent: AppSwitchEvent(timeStamp=1789195375521, eventType=1, targetName=com.hsv2.hsv2_lite, firstStart=false, multiApp=false, windowMode=1, resumingPackageName=, resumingActivityName=, isResumingMultiApp=false, isResumingFirstStart=false)
09-12 12:12:55.523 I/ContextAware_AppRecommendAdapter( 6797): fenceCallbackForAlg receive app recommend from AppSwitchEvent(eventId=1, switchInfo=AppSwitchInfo(targetName=com.hsv2.hsv2_lite, firstStart=false, multiApp=false, windowMode=1, resumingPackageName=, resumingActivityName=, isResumingFirstStart=false, isResumingMultiApp=false))
09-12 12:12:55.524 W/metis_v2_AppSwitchDataCollector( 6797): fenceKey = com.oplus.metis:TOP_APPLICATION, getExits = [], getEnters = [] packageName = com.hsv2.hsv2_lite
09-12 12:12:55.525 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.525 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.528 D/CommandQueueLogger( 5958): onSystemBarAttributesChanged displayId=0 appearance=528 packageName=com.hsv2.hsv2_lite
09-12 12:12:55.528 V/WindowManager( 4178): Relayout Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}: viewVisibility=0, oldvis=0, req=1080x2354 , apr = 0, , x=0, y=0
09-12 12:12:55.529 V/WindowManager( 4178):         {WCT{RemoteToken{94ef390 Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity}}} m=CHANGE f=MOVE_TO_TOP leash=Surface(name=Task=1100)/@0x9e0148c sb=Rect(0, 0 - 1080, 2354) eb=Rect(0, 0 - 1080, 2354) epz=Point(1080, 2354) d=0 taskParent=-1},
09-12 12:12:55.529 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.530 D/SurfaceComposerClient( 4178): Transaction::apply InputWindowCommands.focusRequests timestamp=412778543560036, windowName=4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity
09-12 12:12:55.532 I/statsd  ( 2481): An anomaly (119840000001) (100198)0x10000->com.hsv2.hsv2_lite[S] (100198)0x20000->10547[I]  has occurred! Informing subscribers.
09-12 12:12:55.532 D/ActivityRecordExtImpl( 4178):  setIsWaitingForAppResume for ActivityRecord{145288584 u0 com.hsv2.hsv2_lite/.MainActivity t1100} isWaiting false
09-12 12:12:55.533 I/midasd  ( 7674): app forground called:com.hsv2.hsv2_lite
09-12 12:12:55.536 D/BatteryStatsImplExtImpl( 4178): resumedUid:10547 resumedBatteryLevel:33  resumedPackage:com.hsv2.hsv2_lite  resumedBatteryRealtimeCapacity:1992
09-12 12:12:55.539 D/SurfaceFlinger( 2878): updateWinowInfo=1, setFocusedWindow timestamp=412778543560036, windowName=4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity
09-12 12:12:55.539 I/InputDispatcher( 4178): NFW_setFocusedWindow, 4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity on display 0, same as the previous:0
09-12 12:12:55.539 I/InputDispatcher( 4178): updateFocusedWindow, 4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity on display 0, reason: setFocusedWindow, result:   FocusedWindows:
09-12 12:12:55.539 I/InputDispatcher( 4178):     displayId=0, name='4cdf929 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity'
09-12 12:12:55.540 I/statsd  ( 2481): An anomaly (104240000001) (42)0x10000->10547[I] (42)0x20000->com.hsv2.hsv2_lite[S] (42)0x30000->.MainActivity[S]  has occurred! Informing subscribers.
09-12 12:12:55.546 D/InputMethodManagerService( 4178): isSecurity: attribute.packageName = com.hsv2.hsv2_lite mCurMethodId = com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME isSecurity = false ( enable = false needShow = false inBlackList = false isExist = true )
09-12 12:12:55.546 I/ImeTracker( 4178): com.hsv2.hsv2_lite:8866111e: onRequestHide at ORIGIN_SERVER reason HIDE_UNSPECIFIED_WINDOW fromUser false
09-12 12:12:55.547 I/ImeTracker(15131): com.hsv2.hsv2_lite:8866111e: onCancelled at PHASE_CLIENT_ALREADY_HIDDEN
09-12 12:12:55.548 I/GoogleInputMethodService(21325): GoogleInputMethodService.onStartInput():1532 onStartInput(EditorInfo{EditorInfo{packageName=com.hsv2.hsv2_lite, inputType=0, inputTypeString=NULL, enableLearning=false, autoCorrection=false, autoComplete=false, imeOptions=0, privateImeOptions=null, actionName=UNSPECIFIED, actionLabel=null, initialSelStart=-1, initialSelEnd=-1, initialCapsMode=0, label=null, fieldId=0, fieldName=null, extras=Bundle[{com.oplus.im.WINDOW_MODE=1, com.oplus.im.SCENES=0}], hintText=null, hintLocales=[]}}, false)
09-12 12:12:55.550 W/PackageConfigPersister( 4178): App-specific configuration not found for packageName: com.hsv2.hsv2_lite and userId: 0
09-12 12:12:55.632 D/Transition( 4178):    ChangeInfo{3704da4 container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x20}
09-12 12:12:55.632 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.632 D/OplusTransitionController( 4178):    ChangeInfo{3704da4 container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x20}
09-12 12:12:55.633 D/FlexibleTaskTransitionController( 4178):    ChangeInfo{3704da4 container=Task{195346 #1100 type=standard I=com.hsv2.hsv2_lite/.MainActivity} flags=0x20}
09-12 12:12:55.634 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} mCurrentFocus:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:55.640 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} mCurrentFocus:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:55.640 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.645 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} mCurrentFocus:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:55.645 D/ActivityTaskManager( 4178): The isLandscapeFullscreenEnabled is: com.hsv2.hsv2_lite/.MainActivity
09-12 12:12:55.653 D/WindowManager( 4178): NFW_findFocusedWindowIfNeeded:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity} mCurrentFocus:Window{4cdf929 u0 com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity}
09-12 12:12:55.657 D/ActivityTaskManager( 4178): after skip size:5,pre-Filtered:[com.android.settings 1099, com.android.settings 1098, com.coloros.assistantscreen 800, com.hsv2.hsv2_lite 1100, com.android.settings 1093]
09-12 12:12:55.817 D/RefreshRate( 4178):     | COURSE[; Win preferred rateId=0, appReqFirst=false, data= RateData{} ;win name:com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity]
09-12 12:12:55.883 D/VRR [OPlusGameStatusManager]( 4178): updateGameAccelerationStatus pkgName: com.hsv2.hsv2_lite mOSyncFeatureSwitch: false mIsGamePolicy: false mHeartStop: true mHasFlickerRisk: false mForceAcceleration: false mToastReady: false enable: false
09-12 12:12:56.551 D/OplusScrollToTopManager(15131): com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity,This com.android.internal.policy.DecorView{9dc129c V.E...... R.....I. 0,0-1080,2354 aid=0 alpha=1.0 viewInfo = }[MainActivity] change focus to true
09-12 12:12:56.748 D/VRR [OPlusGameStatusManager]( 4178): updateGameAccelerationStatus pkgName: com.hsv2.hsv2_lite mOSyncFeatureSwitch: false mIsGamePolicy: false mHeartStop: true mHasFlickerRisk: false mForceAcceleration: false mToastReady: false enable: false
09-12 12:12:58.357 D/ProfileInstaller(15131): Installing profile for com.hsv2.hsv2_lite
09-12 12:12:58.739 W/OplusHecate( 2878): layer[86339] 98d0efc SurfaceView[com.hsv2.hsv2_lite/com.hsv2.hsv2_lite.MainActivity](BLAST)#86339 didn't commit buffer within 3000ms, ownerPid 15131
09-12 12:12:59.871 D/ViewExtract(15131): [OplusViewExtractManager] OplusViewExtractManager init com.hsv2.hsv2_lite
09-12 12:12:59.965 D/VRR [OPlusGameStatusManager]( 4178): updateGameAccelerationStatus pkgName: com.hsv2.hsv2_lite mOSyncFeatureSwitch: false mIsGamePolicy: false mHeartStop: true mHasFlickerRisk: false mForceAcceleration: false mToastReady: false enable: false
09-12 12:13:00.140 I/HSV2Native(15131): Native stream thread started!
09-12 12:13:01.037 D/VRR [OPlusGameStatusManager]( 4178): updateGameAccelerationStatus pkgName: com.hsv2.hsv2_lite mOSyncFeatureSwitch: false mIsGamePolicy: false mHeartStop: true mHasFlickerRisk: false mForceAcceleration: false mToastReady: false enable: false
09-12 12:13:01.909 W/LOG_FLOWCTRL(15131): ==LOGS OVER PROC QUOTA(300), rows(101) bytes(7980) com.hsv2.hsv2_lite u(0) l(0) t(0) c(0) DROPPED==
09-12 12:13:02.364 W/FinalizerDaemon(15131): type=1400 audit(0.0:218481): avc:  denied  { getopt } for  path="/dev/socket/usap_pool_primary" scontext=u:r:untrusted_app:s0:c35,c258,c512,c768 tcontext=u:r:zygote:s0 tclass=unix_stream_socket permissive=0 app=com.hsv2.hsv2_lite
09-12 12:13:02.910 W/LOG_FLOWCTRL(15131): ==LOGS OVER PROC QUOTA(300), rows(77) bytes(6461) com.hsv2.hsv2_lite u(0) l(0) t(0) c(0) DROPPED==
09-12 12:13:03.911 W/LOG_FLOWCTRL(15131): ==LOGS OVER PROC QUOTA(300), rows(96) bytes(7429) com.hsv2.hsv2_lite u(0) l(0) t(0) c(0) DROPPED==
09-12 12:13:04.912 W/LOG_FLOWCTRL(15131): ==LOGS OVER PROC QUOTA(300), rows(101) bytes(7916) com.hsv2.hsv2_lite u(0) l(0) t(0) c(0) DROPPED==
09-12 12:13:05.913 W/LOG_FLOWCTRL(15131): ==LOGS OVER PROC QUOTA(300), rows(81) bytes(6422) com.hsv2.hsv2_lite u(0) l(0) t(0) c(0) DROPPED==
09-12 12:13:06.913 W/LOG_FLOWCTRL(15131): ==LOGS OVER PROC QUOTA(300), rows(81) bytes(6368) com.hsv2.hsv2_lite u(0) l(0) t(0) c(0) DROPPED==
