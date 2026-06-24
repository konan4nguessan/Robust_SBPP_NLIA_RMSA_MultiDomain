# RMSA Net2Plan Integration Notes

Ce fichier suit la couche d'integration Net2Plan. Le principe architectural est de garder `rmsa.core` independant de Net2Plan, puis de placer tous les mappings dans `rmsa.net2plan`.

## 1. Frontiere d'adaptation

Comme le jar Net2Plan n'est pas present dans le workspace, l'integration commence avec des interfaces minimales compilables:

- `Net2PlanLinkView`
- `Net2PlanDemandView`
- `Net2PlanPathView`

Ces interfaces representent les informations necessaires au coeur RMSA: identifiant de lien, noeuds origine/destination, longueur, demande source/destination/debit et sequence de liens d'un chemin.

## 2. Conversion vers le coeur

`Net2PlanCoreAdapter` convertit:

- les liens Net2Plan-like vers des `Link` du coeur;
- une demande vers `ConnectionRequest`;
- un chemin Net2Plan-like vers `NetworkPath`.

Les identifiants de liens Net2Plan peuvent etre non compacts. `AdaptedNetwork` maintient donc un mapping `external link id -> core link id` et le mapping inverse.

## 3. Provisioning initial

`RmsaCoreProvisioner` recoit des paires candidates `(working path, backup path)` deja adaptees en objets core. Il teste les CFSS working et backup robustes, simule les allocations candidates sur une copie du spectre, calcule la fonction objectif, puis retourne la combinaison faisable de plus petit score.

La fonction objectif utilise la forme du papier:

```text
sum_i(T_i + R_i) / sum_l(F - H_l)
```

Dans cette premiere integration, le cout Tx/Rx est encore simplifie: chaque slot working et backup ajoute un Tx et un Rx. La partie spectrale `sum_l(F - H_l)` est calculee sur le spectre simule apres allocation.

## 4. Jars et projet de reference

Les jars Net2Plan 0.7.0.1 referencÃ©s par le projet Eclipse `MultiDomain_SBPP_CFSS_improved` ont ete copies dans `lib/net2plan-0.7.0.1/`. Le code source du projet improved a ete copie comme reference dans `references/MultiDomain_SBPP_CFSS_improved/`.

## 5. Adaptateurs Net2Plan concrets

Le package `rmsa.net2plan.actual` contient maintenant des wrappers qui importent les classes Net2Plan reelles:

- `ActualNet2PlanLinkView`
- `ActualNet2PlanDemandView`
- `ActualNet2PlanPathView`
- `ActualNet2PlanAdapterFactory`

`Net2PlanDecisionApplier` traduit une `RmsaProvisioningDecision` en routes Net2Plan: une route working et une route backup, avec des attributs stockant role, identifiant de connexion, slots et modulations.

## 6. Processeur dynamique initial

`OnlineEvProc_RobustSbppRmsa` est une premiere version legere d'un `IEventProcessor` Net2Plan. Elle reprend l'architecture du projet de reference `MultiDomain_SBPP_CFSS_improved`, mais en remplaÃ§ant l'ancien moteur par le nouveau coeur `rmsa.core`.

Le processeur gere:

- `SimEvent.RouteAdd`: generation de paires candidates working/backup, admission via `RmsaCoreProvisioner`, creation des routes Net2Plan, reservation dans `SpectrumState`, rafraichissement du snapshot NLI;
- `SimEvent.RouteRemove`: liberation du spectre, suppression des routes Net2Plan associees, rafraichissement du snapshot.

`Net2PlanCandidatePathBuilder` fournit pour l'instant une generation simple de K chemins working et K backups link-disjoint par DFS puis tri par longueur. Cette brique pourra etre remplacee plus tard par les chemins candidats natifs Net2Plan ou une implementation de Yen plus efficace.

## 7. Metriques dynamiques initiales

`RobustSbppRmsaMetrics` collecte les compteurs de simulation dynamiques: arrivees, acceptations, blocages, departs, debit demande, debit accepte, debit bloque, BBP et fragmentation moyenne.

`OnlineEvProc_RobustSbppRmsa.finish` ajoute un rapport textuel Net2Plan avec ces metriques. La fragmentation suit la formule du papier par lien:

```text
1 - largest continuous free FS block / total free FS
```

## 8. Tx/Rx et snapshot NLI dynamique

`OnlineEvProc_RobustSbppRmsa` initialise maintenant un `TransceiverState` avec les parametres `txPerNode` et `rxPerNode`. Le provisioner evalue donc les candidats avec la disponibilite Tx/Rx reelle du coeur, puis le processeur reserve/libere ces ressources en meme temps que le spectre.

Lors d'une arrivee acceptee, le processeur construit la liste des slots working/backup acceptes et appelle `NliSnapshotStore.updateAfterAcceptedAllocation`, ce qui suit la logique recursive du papier.

Lors d'un depart, le processeur construit la liste symetrique des slots liberes et appelle `NliSnapshotStore.updateAfterReleasedAllocation`. Ainsi, la simulation dynamique utilise maintenant un snapshot NLI incremental pour les arrivees et pour les departs.

## 9. Topologie 14 noeuds et generateur de trafic

La topologie Net2Plan `example14nodes_robust_sbpp_avec_demandes.n2p` a ete copiee depuis le workspace Net2Plan vers `data/networkTopologies/example14nodes_robust_sbpp_avec_demandes.n2p`. C'est la topologie de reference pour lancer les simulations proches de l'article.

`OnlineEvGen_RobustSbppTraffic` est le generateur dynamique dedie au projet actuel. Il reprend la logique utile du generateur de reference `OnlineEvGen_MultiDomain_SBPP_DADRSA`: evenements `RouteAdd`, evenements `RouteRemove`, arrivees aleatoires, durees de vie aleatoires et debit de connexion aleatoire. Les valeurs par defaut sont maintenant celles de l'article: debit uniforme entre `50` et `700 Gb/s`, duree moyenne `1 h`, arrivees et durees exponentielles.

Le test `Example14NodesTopologySmokeTest` verifie que la topologie locale se charge correctement, qu'elle contient 14 noeuds et des demandes, et qu'elle peut etre adaptee vers le coeur RMSA.

Attention pratique: les jars Net2Plan contiennent plusieurs versions de `ConcurrencyUtils`. Le script `scripts/compile_with_net2plan.ps1` force maintenant `parallelcolt-parallelcolt-0.11.4.jar` avant les autres jars pour eviter une erreur de chargement lors de l'ouverture des fichiers `.n2p`.

## 10. Visualisation Net2Plan du spectre et des protections

`Net2PlanSpectrumSynchronizer` projette maintenant l'etat interne `SpectrumState` dans les attributs des liens Net2Plan, comme dans le projet de reference. Les attributs ecrits sont:

- `SPECTRUM`: chaine de slots, avec `.` pour libre, `W` pour working et `B` pour backup reserve;
- `carriedSlots`: nombre de slots working;
- `reservedSlots`: nombre de slots backup reserves;
- `occupiedSlots`: nombre total de slots non libres;
- `occupancyRatio`: fraction de slots occupes sur le lien.

`OnlineEvProc_RobustSbppRmsa` synchronise ces attributs a l'initialisation, apres chaque allocation acceptee et apres chaque liberation.

Les routes Net2Plan contiennent deja `rmsaRole`, `rmsaConnectionId`, `rmsaSlots` et `rmsaModulations`. Elles exposent maintenant aussi `rmsaBackupRouteId` sur la route working et `rmsaWorkingRouteId` sur la route backup, en plus du lien Net2Plan natif `workingRoute.addBackupRoute(backupRoute)`.

## 11. Pannes bidirectionnelles et backup actif

Le projet de reference traite les deux liens diriges entre les memes noeuds comme un meme risque physique. Cette logique est maintenant reprise dans le projet actuel:

- une panne Net2Plan `NodesAndLinksChangeFailureState` sur un lien `u -> v` met aussi en panne le lien retour `v -> u` s'il existe;
- la reparation d'un lien repare aussi son lien retour;
- les connexions dont le working path traverse le risque physique en panne passent en etat backup actif;
- le synchroniseur de spectre affiche `A` pour les slots backup actifs;
- les routes exposent `rmsaProtectionState` avec `WORKING_ACTIVE`, `FAILED_WORKING`, `STANDBY` ou `BACKUP_ACTIVE`.

La notion de disjonction a aussi ete durcie: `NetworkPath.isLinkDisjointWith` compare maintenant les risques physiques non orientes `(min(nodeA,nodeB), max(nodeA,nodeB))`, et `Net2PlanCandidatePathBuilder` interdit aux backups d'utiliser le lien retour d'un lien working. Cela colle mieux au modele bidirectionnel du projet de reference.

## 12. Injection de panne configurable

`OnlineEvProc_RobustSbppRmsa` expose maintenant des parametres Net2Plan pour piloter une panne deterministe comme dans le projet de reference:

- `enableFailureInjection`: active/desactive l'injection;
- `failureInjectionMode`: `TIME_BASED` ou `AFTER_N_ACCEPTED`;
- `failureTime`: instant de panne en secondes pour le mode temporel;
- `repairTime`: instant de reparation en secondes, ou `-1` pour ne pas reparer;
- `failureLinkId`: identifiant Net2Plan du lien de reference;
- `failureAfterAcceptedConnections`: seuil d'acceptations pour le mode `AFTER_N_ACCEPTED`;
- `revertiveMode`: repasse les backups actifs en standby apres reparation.

Le lien choisi reste traite comme un risque bidirectionnel: une panne sur `u -> v` applique aussi la panne au lien retour `v -> u` lorsqu'il existe. La reparation suit la meme logique.

## 13. Debit dynamique des evenements RouteAdd

Le processeur utilisait initialement `demand.getOfferedTraffic()` pour dimensionner une nouvelle connexion. C'etait incorrect en simulation dynamique: `getOfferedTraffic()` represente le trafic offert moyen de la demande Net2Plan, alors que chaque evenement `SimEvent.RouteAdd` porte son propre debit dans `carriedTraffic`.

`OnlineEvProc_RobustSbppRmsa` utilise maintenant `event.carriedTraffic` pour:

- enregistrer les metriques demande/accepte/bloque;
- creer le `ConnectionRequest` envoye au coeur RMSA;
- creer la route working Net2Plan avec le bon debit porte.

La route working et la route backup utilisent maintenant `event.occupiedLinkCapacity`, comme dans le projet de reference. Dans le generateur de reference, cette valeur est egale au debit de connexion (`new RouteAdd(demand, null, connectionSize, connectionSize)`). Le backup garde `carriedTraffic=0.0`, mais reserve la meme capacite occupee Net2Plan que la connexion protegee.

Le nombre de slots effectivement alloues reste expose separement via `rmsaSlots`, `carriedSlots`, `reservedSlots` et `occupiedSlots`.

## 14. Profil robuste brut unique

Le processeur de contribution multidomaine ne propose plus de mode accelere branche dans le coeur. `OnlineEvProc_RobustSbppRmsa` utilise la chaine scientifique robuste brute: generation working/backup CFSS, partage SBPP, validation QoT/NLIA, bitloading, scenarios de panne du papier et objectif global.

Les accelerations qui modifiaient le comportement du coeur ont ete retirees: preselection rapide globale, filtre QoT niveau 1, limitation du nombre de validations completes, scenarios working cibles et cache NLI/SINR optionnel. Le broker multidomaine assemble encore les couples candidats, mais il les transmet tous au coeur robuste brut.

Les parametres experimentaux d'acceleration ont ete retires de l'interface active. Les parametres visibles conservent la recherche de chemins, les ressources Tx/Rx, la panne/restauration, la visualisation et les traces.

## 15. Rapprochement avec le projet de reference

Deux idees du projet `Robust_RMSA_SBPP` ont ete rapatriees dans le nouveau projet.

Premier point: le coeur scientifique recoit maintenant les liens reels lors de l'adaptation Net2Plan. `SpectrumState` construit alors les groupes de panne bidirectionnels, et les scenarios robustes utilisent ces groupes au lieu de simples liens diriges. La couche Net2Plan et le coeur RMSA parlent donc la meme notion de risque physique.

Deuxieme point: la synchronisation visuelle Net2Plan devient configurable. `OnlineEvProc_RobustSbppRmsa` expose `enableVisualization` et `uiRefreshEveryNEvents`. Les attributs lourds des liens (`SPECTRUM`, `carriedSlots`, `reservedSlots`, `occupiedSlots`, `occupancyRatio`) sont rafraichis periodiquement pendant les arrivees/departs, et immediatement lors des pannes/reparations. Mettre `uiRefreshEveryNEvents=1` restaure le comportement complet evenement par evenement; une valeur plus grande accelere les simulations longues.

## 2026-05-18 - Trace historique et indicateurs spectraux

Ajout d'une trace historique optionnelle vers fichier dans `OnlineEvProc_RobustSbppRmsa` (`scientificTraceEnabled`, `scientificTracePath`, `scientificTraceLevel`, `scientificTraceConnectionId`, `scientificTraceBlockedOnly`). `scientificTraceLevel` est une liste Net2Plan (`SUMMARY`, `ADMISSION`, `FULL`) et `scientificTracePath` pointe par defaut vers `C:\Users\louis\Downloads\robust_nlia_trace_multidomain_contribution.txt`. La console Java reste controlee par `debug`; le fichier raconte les arrivees, candidats, blocages, selections, acceptations, departs, pannes et backups actifs.

Ajout aussi d'attributs de liens plus lisibles dans `Net2PlanSpectrumSynchronizer`: `activeBackupSlots`, `freeSlots`, `spectrumOccupiedPercent`, `spectrumCarriedPercent`, `spectrumReservedPercent`, `spectrumActiveBackupPercent`, `spectralStatus`, `spectralSummary`, `trafficCarriedGbps`, `trafficOccupiedGbps`, `trafficLoadPercent`, `spectralLoadPercent`. Le rapport `RobustSbppRmsaMetrics` expose maintenant `totalCarriedSlots`, `totalReservedSlots`, `totalOccupiedSlots`, `totalFreeSlots`, `averageLinkSpectralOccupancy` et `maxLinkSpectralOccupancy`.

## 2026-05-18 - Metriques panne/restauration dans Robust_SBPP_NLIA_RMSA

Ajout des metriques explicites de panne et de restauration dans le rapport Net2Plan du simulateur robuste: `failureEvents`, `repairEvents`, `lastFailedBidirectionalLinks`, `lastAffectedConnections`, `lastActivatedBackups`, `activeBackupConnections`, `lastAffectedGbps`, `lastRestoredGbps`, `lastLostGbps`, `lastRestorationSuccessRatio`, ainsi que les cumuls correspondants. La trace externe detaillee recoit aussi des lignes `FAILURE-RESTORATION` et `REPAIR-RESTORATION`, ce qui permet de relier une panne bidirectionnelle aux connexions impactees et aux backups actives.

## 2026-05-20 - Nettoyage final avant modele hybride

Suppression des residus de profil de reference et des toggles experimentaux. Le projet de contribution garde seulement le coeur robuste brut. Le rapport continue d'exposer les compteurs scientifiques fins: `robustQoTValidations`, `existingQoTValidations`, `failureScenariosEvaluated`, `nliSinrComputations`, `candidatePairsSentToCore` et les temps CPU par demande.
## 2026-05-20 - Attributs multi-domaines initiaux

Le clone multidomaine ajoute une premiere couche broker/controlleurs locaux pour identifier les domaines depuis les attributs des noeuds, construire les candidats avec information de domaine et exposer dans Net2Plan les attributs de route `interDomain`, `domainPath` et `domainTransitions`. Le rapport distingue aussi les arrivees, acceptations, blocages et debits intra-domaine/inter-domaine. Cette base reste volontairement compatible avec le coeur robuste brut.

## 2026-05-20 - Architecture hybride broker/controlleurs locaux

Le projet multidomaine utilise maintenant une architecture hybride. Les controlleurs locaux representes par `LocalDomainController` decomposent les chemins en `LocalDomainSegment`, lisent l'occupation spectrale locale dans `SpectrumState`, verifient l'existence d'une fenetre minimale de slots pour les segments working et filtrent les segments localement trop charges via `hybridLocalMaxLinkOccupancy`.

Le `GlobalSdnBroker` conserve la vue globale: il construit les chemins de domaines, demande aux controlleurs locaux de filtrer les chemins intra/inter-domaines, verifie la disjonction physique SBPP working/backup au niveau inter-domaine, assemble les couples candidats, puis transmet tous ces couples au coeur global QoT/NLIA brut. Le coeur scientifique reste donc centralise pour la decision finale: validation robuste, NLI/SINR, bitloading, objectif et allocation.

Le rapport Net2Plan expose maintenant les compteurs hybrides `hybridLocalPathsEvaluated`, `hybridLocalPathsRejected`, `hybridSbppRejectedPairs`, `hybridBrokerAssembledPairs` et `hybridFallbackRequests`. La trace d'admission ajoute aussi un resume `hybrid=...` pour raconter le travail du broker et des controlleurs locaux sur chaque demande.
## 2026-05-20 - Broker d'admission hybride

Le coeur robuste n'est plus appele directement par `OnlineEvProc_RobustSbppRmsa`. L'appel passe derriere `GlobalSdnBroker`. Le processeur Net2Plan recoit l'evenement, calcule le debit demande, puis delegue l'admission au broker multidomaine.

`GlobalSdnBroker` execute maintenant la chaine complete: demande de candidats au builder multidomaine, prise en compte des filtres des controlleurs locaux, appel a `RmsaCoreProvisioner.chooseFirstFeasible` sur tous les couples construits, puis retour d'une `GlobalAdmissionResult` contenant la decision robuste et les compteurs globaux. Cette organisation colle mieux a une architecture SDN: Net2Plan orchestre les evenements, les controlleurs locaux exposent l'etat local, le broker coordonne et appelle le moteur scientifique global.

## 24. Coeur ordonne selon l'article

Le coeur multidomaine utilise maintenant un profil `PAPER_ORDERED_SBPP_NLIA`. Pour chaque working path, `RmsaCoreProvisioner` evalue d'abord les CFSS working en mode strict et retient le meilleur CFSS working selon la fonction objectif simulee avant de tester les backups associes. Cela rapproche l'ordre d'execution de l'algorithme du papier: working CFSS d'abord, backups ensuite, puis objectif final.

La verification QoT des connexions existantes est de nouveau appelee pendant la construction CFSS, slot par slot. `RobustCfssWorkingEvaluator` controle les allocations working hypothetiques au fur et a mesure. `RobustCfssBackupEvaluator` applique la meme logique pour `CFSSb`, en ajoutant le working deja retenu dans les allocations hypothetiques avant de tester chaque slot backup. Une verification finale sur le candidat complet working+backup reste presente comme garde-fou avant le calcul de l'objectif global.
