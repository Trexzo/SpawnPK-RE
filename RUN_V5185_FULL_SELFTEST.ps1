param([string]$Target=$PSScriptRoot,[string]$ConfigDir=(Join-Path $env:USERPROFILE '.spawnpk\configs'))
Set-StrictMode -Version 2.0
$ErrorActionPreference='Stop'
$lab=(Resolve-Path -LiteralPath $Target).Path
$selector=Join-Path $lab 'tools\R85_SelectJava11Plus.ps1'
if(-not(Test-Path -LiteralPath $selector -PathType Leaf)){throw "Missing R8.5 Java selector: $selector"}
$hadCallerJavaHome=Test-Path Env:JAVA_HOME
$callerJavaHome=$env:JAVA_HOME
$callerPath=$env:Path
try{
. $selector
$javaInfo=Set-R85Java11Plus
$javaExe=$javaInfo.Path
$jar=Join-Path $lab 'server\build\SpawnPKLocalServer.jar'
$expected='589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c'
$historicalClientV307='6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662'
$expectedClient='854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'
if(-not(Test-Path -LiteralPath $jar -PathType Leaf)){throw "Missing server JAR: $jar"}
$actual=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if($actual -ne $expected -and $env:SPK_ALLOW_DEV_BUILD -ne '1'){throw "v5.18.5 server hash mismatch. expected=$expected actual=$actual"}
$client=$null
foreach($p in @((Join-Path $lab 'evidence\client(6).jar'),(Join-Path $lab 'evidence\client(4).jar'),(Join-Path $lab 'evidence\client.jar'),(Join-Path $lab 'client.jar'))){if(Test-Path -LiteralPath $p -PathType Leaf){if((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant() -eq $expectedClient){$client=$p;break}}}
if(-not $client){throw 'Pinned exact client.jar not found for parity tests.'}
$tests=@(
    'NormalInventoryDragEquipTest',
    'NormalInventoryDragRuntimeIntegrationTest',
    'StackableAmmoEquipmentTest',
    'AmmoPersistenceTest',
    'ItemOnItemOpcode53Test',
    'DoppelRemoveDyeTest',
    'PetDefinitionRepositoryTest',
    'ScoobyBehemothPetTest',
    'ResvanoColorCycleTest',
    'PetFollowAdjacencyTest',
    'PetReplacementRuntimeTest',
    'PetRuntimeIntegrationTest',
    'NpcActionRoutingPolicyTest',
    'CombatNpcAttackOpcode72Test',
    'CombatTargetRepositoryTest',
    'CombatWeaponCoverageTest',
    'CombatPlayer81AnimationClientParityTest',
    'CombatNpc65HitClientParityTest',
    'CombatEngineM1Test',
    'NpcPacket65DynamicClientParityTest',
    'CombatInterfaceRepositoryTest',
    'BottomSidebarProductionParityTest',
    'CollectionIconAppearanceTest',
    'CompCapeCustomizationTest',
    'HomeWorldParityDataTest',
    'HomeScenePacketCodecTest',
    'HomeNpcWorldStateTest',
    'HomeCollisionOverlayPlanTest',
    'HomeWorldRuntimePlanTest',
    'HomeSceneRuntimeBaseTest',
    'HomeWorldV55IntegrationTest',
    'MovementRuntimeIntegrationTest',
    'EquipmentRuntimeIntegrationTest',
    'EquipmentMultiSlotRuntimeIntegrationTest',
    'ClientPacketFramingAuthorityTest',
    'SpawnTabClientParityTest',
    'SpawnTabRuntimeIntegrationTest',
    'AccountPersistenceTest',
    'PlayerStateClientParityTest',
    'NativeIconFamilyTest',
    'NurseStatePresentationTest',
    'ScopesightNativePresentationTest',
    'V58StatePersistenceTest',
    'NpcPacket65ForceTextCodecTest',
    'NpcPacket65GfxCodecTest',
    'BehemothChargeStateTest',
    'PetHiddenStatePresentationTest',
    'PetOwnerLifecyclePresentationTest',
    'PetPresentationProfileTest',
    'DevAuthorityWorkbenchM2Test',
    'DevInventorySpritePreviewTest',
    'NpcSpawnPresentationWorkbenchTest',
    'PlayerPresentationIsolationTest',
    'PlayerNpcMorphAppearanceTest',
    'DevNpcServiceTest',
    'DevProtocolTraceTest',
    'DevAssetBrowserTest',
    'DevAuthorityWorkbenchM3Test',
    'SpecialPetVisualLabTest',
    'DevPetVariantBindingTest',
    'PetPickupFacingTest',
    'PrayerMagicCombatRuntimeTest',
    'SpellTargetCodecTest',
    'PrayerMagicStyleRuntimeIntegrationTest',
    'WeaponAttackAuthorityR2Test',
    'WeaponAttackRuntimeIntegrationTest',
    'EngineR1FoundationTest',
    'CosmeticDedicatedSlotTest',
    'GroundItemSemanticTest',
    'NpcInteractionRouterTest',
    'MiniPetSubsystemTest',
    'SharedWorldOwnershipTest',
    'WorldPulseCommandExecutionTest',
    'WorldCommandLifecycleFenceTest',
    'OutboundPacketQueueTest',
    'SharedWorldSocketIntegrationTest',
    'WorldCommandFairnessTest',
    'WorldCommandLatencyTest',
    'CombatMovementIntentTest',
    'GroundTakeExactTilePolicyTest',
    'RuntimeCorrectiveIntegrationTest',
    'CombatServerOwnedApproachTest',
    'MiniPetFollowSpeedTest',
    'SecondaryAccountSelectionTest',
    'LocalAccountProfilesTest',
    'DualProfileSocketIntegrationTest',
    'VoidglassPetProfileTest',
    'VoidglassRuntimeIntegrationTest',
    'MeleeCardinalRangeTest',
    'CosmeticEquipmentWidgetTest',
    'LocalDevVisualOverrideStoreTest',
    'LogoutButtonIntegrationTest',
    'AuthorityR16R25ContractTest',
    'BankAuthorityR18BehaviorTest',
    'AuthorityR22ScriptPacket250Test',
    'PetBreadcrumbTrainTest',
    'ScoobyBehemothDropParityTest',
    'PetRouteReplacementTest',
    'PetDropFacingEgressTest',
    'MiniPetConfigureContractTest',
    'CombatOverlayHpTest',
    'PetBrokenTrailRecoveryTest',
    'ClientPacket57ItemOnNpcTest',
    'PetAccessoryContractTest',
    'ScoobyFourWayLocalExtensionTest',
    'DialogCloseOnMovementTest',
    'OpponentOverlayClearGuardTest',
    'PetPickupTimingContractTest',
    'PetCatchupCadenceTest',
    'HomeCombatCollisionPathTest',
    'DevHitDamageLabTest',
    'PetAccessoryDialogTest',
    'DialogNumberKeyStateTest',
    'HitmarkSemanticSelectionTest',
    'PetCatchupSchedulerContinuityTest',
    'CombatImmediateTargetFacingTest',
    'PetPickupSynchronousRemovalTest',
    'PositionPersistenceR213Test',
    'Player81MeasuredSyncTest',
    'PositionRestorePacket81ProjectionTest',
    'EngineR3PlayerSyncTest',
    'EngineR3PlayerOptionCodecTest',
    'EngineR3PetAccessoryPersistenceTest',
    'EngineR3DualSocketVisibilityTest',
    'EngineR31PresentationTradeTest',
    'EngineR31SharedNpcRelayTest',
    'EngineR32WorldPresentationBusTest',
    'EngineR4TradeFlowTest',
    'EngineR4NativeEquipmentDeathUiTest',
    'EngineR4TradeSocketOpenTest',
    'EngineR41TradePresentationStateTest',
    'EngineR41V913WeaponAuthorityTest',
    'EngineR41TradeApproachSocketTest',
    'EngineR5ItemAuthorityTest',
    'EngineR5WorldAuthorityTest',
    'EngineR5NativeItemLibraryTest',
    'EngineR5ItemLibrarySocketTest',
    'EngineR51V913TrajectorySoundTest',
    'EngineR52DeathPolicyClassificationTest',
    'EngineR6WorldCollisionAuthorityTest',
    'EngineR6RegionProjectionTest',
    'EngineR6RegionSocketCommandTest',
    'EngineR7ContentAuthorityTest',
    'EngineR7DevPanelSocketTest',
    'EngineR71AlignmentContractTest',
    'EngineR71AuthorityBrowserTest',
    'EngineR72RuntimeWeaponLabTest',
    'EngineR72RuntimeWeaponPanelSocketTest',
    'EngineR8ResearchExhaustionTest',
    'EngineR8ResearchPanelSocketTest',
    'EngineR81ProjectilePromotionTest',
    'EngineR81DescriptionInheritanceTest',
    'EngineR81PetPickupFreezeTest',
    'EngineR81EquipstrFailClosedSocketTest',
    'EngineR81SceneStreamingTest',
    'EngineR81ConfigHoverPatchTest',
    'EngineR82FullResearchArchiveTest',
    'EngineR82ResearchDetailTest',
    'EngineR82ResearchPanelSocketTest',
    'EngineR83ClientDiscoveryAuthorityTest',
    'EngineR83DiscoveryPanelSocketTest',
    'EngineR84ApplicationProtocolAuthorityTest',
    'EngineR84Packet250WriterTest',
    'EngineR84ApplicationPanelSocketTest',
    'VoidglassR2CustomContentTest',
    'VoidglassR2RuntimeTest',
    'VoidglassR2ConfigPatchToolTest',
    'VoidglassR2PanelSocketTest',
    'InventoryActionRouterTest',
    'C2S16InventoryOption3DecodeTest',
    'OverrideCosmeticStateTest',
    'OverrideSocketIntegrationTest',
    'EngineR85ApplicationProtocolAuthorityTest',
    'ApplicationUiServiceTest',
    'ApplicationUiFixtureServiceTest',
    'VoidglassR3ConfigPatchToolTest',
    'VoidglassR3CustomContentTest',
    'R85GenericC2SDecodeTest',
    'R85GenericC2SProbeIntegrationTest'
)
$cpParts=@($jar)
if($env:SPK_ALLOW_DEV_BUILD -eq '1'){
  $gradleTests=Join-Path $lab 'server\build\classes\java\test'
  if(Test-Path -LiteralPath $gradleTests -PathType Container){$cpParts+=$gradleTests}
}
$cpParts+=$client
$cp=$cpParts -join ';'
function Invoke-JavaSelfTest([string]$TestName,[int]$TimeoutMs=90000){
  $className='spk.local.'+$TestName
  $psi=New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName=$javaExe
  $extra=if(($TestName -eq 'VoidglassR2ConfigPatchToolTest') -or ($TestName -eq 'VoidglassR3ConfigPatchToolTest')){('-Dspk.voidglass.configDir="{0}" ' -f $ConfigDir)}else{''}
  $psi.Arguments=$extra+('-cp "{0}" {1}' -f $cp,$className)
  $psi.WorkingDirectory=$lab
  $psi.UseShellExecute=$false
  $psi.CreateNoWindow=$true
  $psi.RedirectStandardOutput=$true
  $psi.RedirectStandardError=$true
  $p=New-Object System.Diagnostics.Process
  $p.StartInfo=$psi
  try{
    if(-not $p.Start()){throw "Failed to start Java selftest: $TestName"}
    $outTask=$p.StandardOutput.ReadToEndAsync();$errTask=$p.StandardError.ReadToEndAsync()
    if(-not $p.WaitForExit($TimeoutMs)){
      try{$p.Kill()}catch{};try{[void]$p.WaitForExit(5000)}catch{}
      $stdout='';$stderr='';try{$stdout=$outTask.Result}catch{};try{$stderr=$errTask.Result}catch{}
      if($stdout){$stdout.TrimEnd() -split "`r?`n"|ForEach-Object{Write-Host $_}}
      if($stderr){$stderr.TrimEnd() -split "`r?`n"|ForEach-Object{Write-Host $_ -ForegroundColor DarkRed}}
      throw "Selftest watchdog timeout after $TimeoutMs ms: $TestName"
    }
    $p.WaitForExit();$stdout=$outTask.Result;$stderr=$errTask.Result
    if($stdout){$stdout.TrimEnd() -split "`r?`n"|ForEach-Object{Write-Host $_}}
    if($stderr){$stderr.TrimEnd() -split "`r?`n"|ForEach-Object{Write-Host $_ -ForegroundColor DarkRed}}
    if($p.ExitCode -ne 0){throw "Selftest failed rc=$($p.ExitCode): $TestName"}
  }finally{if($null -ne $p){$p.Dispose()}}
}
function Invoke-JavaSelfTestWithRetry([string]$TestName,[int]$TimeoutMs=90000){for($attempt=1;$attempt -le 2;$attempt++){try{Invoke-JavaSelfTest -TestName $TestName -TimeoutMs $TimeoutMs;if($attempt -eq 2){Write-Host ("R85_SELFTEST_RETRY_PASS test={0} attempt=2" -f $TestName) -ForegroundColor Yellow};return}catch{if($attempt -ge 2){throw};Write-Host ("R85_SELFTEST_RETRY test={0} reason={1}" -f $TestName,$_.Exception.Message) -ForegroundColor Yellow;Start-Sleep -Milliseconds 400}}}
$count=0
foreach($t in $tests){Write-Host ("[V5185-R85] {0}/{1} {2}" -f ($count+1),$tests.Count,$t);Invoke-JavaSelfTestWithRetry -TestName $t -TimeoutMs 90000;$count++}
if($count -ne 179){throw "Unexpected selftest count: $count"}
Write-Host 'V5185_FULL_SELFTEST_PASS count=179 inheritedR842=172/172 r85=7/7 exactClient=true bytecodeMajor=55 maxAttemptsPerTest=2' -ForegroundColor Green
}finally{
  if($hadCallerJavaHome){$env:JAVA_HOME=$callerJavaHome}else{Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue}
  $env:Path=$callerPath
}
