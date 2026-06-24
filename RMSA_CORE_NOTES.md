# RMSA Core Notes

Ce fichier suit la construction du coeur indépendant de Net2Plan pour reproduire progressivement la logique du papier "Robust RMSA design for shared backup path protection based EON against nonlinear impairments".

## 1. Socle slot / spectre / SBPP

La premiere brique codee est le modele de slots. Un `SlotState` represente un slot donne sur un lien donne. Il distingue un proprietaire working unique et plusieurs proprietaires backup possibles. Cette distinction est essentielle pour SBPP: un working ne partage jamais un slot, alors qu'un backup peut partager un slot backup existant si les working paths correspondants sont link-disjoint.

`SpectrumState` contient la matrice `link x slot`. Il expose les operations de reservation working et backup, ainsi que les recherches de blocs contigus utilisables.

## 2. Chemins, demandes et connexions

`Link`, `NetworkPath`, `ConnectionRequest` et `Connection` representent le reseau minimal. Une `Connection` contient un working path et un backup path, avec verification que ces deux chemins sont link-disjoint.

## 3. Modulations du papier

`ModulationFormat` encode les formats utilises dans le papier: BPSK, QPSK, 8-QAM et 16-QAM. Chaque modulation contient son efficacite spectrale, son debit par FS et son seuil SINR en dB.

## 4. CFSS

`ContiguousSlotBlock` represente un bloc contigu candidat, par exemple `[2..4]`. `SpectrumState` expose des methodes pour obtenir les blocs candidats working et backup. Cote working, les slots doivent etre strictement libres. Cote backup, les slots peuvent etre libres ou partageables selon la regle SBPP.

## 5. Squelette CFSSw

`CfssWorkingEvaluator` est le debut de l'algorithme `CFSSw` du papier. Il part du premier slot d'un bloc candidat, ajoute les slots contigus un par un, choisit une modulation pour chaque slot et s'arrete quand le debit demande est atteint. Au debut, le choix de modulation etait fourni par `FixedModulationAssigner`.

## 6. Fonction objectif

`ObjectiveFunction` implemente la premiere forme de l'objectif du papier:

```text
sum_i(T_i + R_i) / sum_l(F - H_l)
```

Pour l'instant les Tx/Rx ne sont pas encore modelises comme objets complets; la fonction recoit seulement les nombres utilises.

## 7. Modele physique unitaire

`PhysicalLayerParameters` contient les parametres fibre/systeme: gamma, beta2, alpha, largeur de slot, puissance signal, longueur de span, noise figure et frequence optique.

`PhysicalLayerModel` calcule les briques physiques unitaires:

- SCI par span,
- XCI par span entre deux slots,
- ASE par lien et par chemin,
- SINR lineaire et SINR dB.

`InterferenceBreakdown` separe SCI, XCI et NLI totale. `SlotQoT` regroupe NLI, ASE, SINR et modulation maximale supportee.

## 8. Scenarios de panne et allocations actives

`FailureScenario` represente `NO_FAILURE` ou `FAILED_LINK(e)`. `SpectrumState.activeAllocationsOnLink` retourne les allocations actives sur un lien selon le scenario:

- sans panne: seuls les working paths sont actifs;
- avec panne: les working paths touches par le lien tombe sont inactifs et leurs backups deviennent actifs;
- les autres working paths restent actifs.

Cette brique materialise le point central du papier: l'ensemble des chemins actifs, donc la NLI, change selon le lien en panne.

## 9. NLI reseau et QoT robuste

`NetworkNliEvaluator` calcule la NLI d'un slot cible sur un chemin a partir des allocations actives. Pour chaque lien du chemin, il ajoute la SCI du slot cible et la XCI produite par les slots actifs sur ce lien.

`RobustSlotQoT` represente le resultat robuste: `NLImax`, `SINRmin`, scenario pire cas et modulation maximale supportee.

## 10. Generation automatique des scenarios robustes

`RobustScenarioGenerator` encode les deux regles du papier:

- pour un working path: `NO_FAILURE` plus toutes les pannes de liens hors du working path candidat;
- pour un backup path: uniquement les pannes des liens du working path correspondant.

`NetworkNliEvaluator` expose ensuite `robustQoTForWorkingPath` et `robustQoTForBackupPath`, qui appliquent automatiquement ces regles.

## 11. Debut du robust bitloading

`SlotModulationAssigner` a ete enrichi pour recevoir `Yslot` et `Ymod`, c'est-a-dire les slots et modulations deja choisis pour la connexion courante. C'est necessaire car, dans le papier, un nouveau slot ajoute de la XCI sur les slots deja decides de la meme connexion.

`NetworkNliEvaluator` peut maintenant calculer une QoT robuste en tenant compte des slots deja choisis de la meme connexion. Cela prepare le mecanisme de degradation eventuelle des anciennes modulations dans l'algorithme 3.

`RobustWorkingModulationAssigner` remplace progressivement `FixedModulationAssigner` pour les working paths. Pour un slot candidat, il calcule `SINRmin` via `robustQoTForWorkingPath`, puis retourne la modulation la plus haute dont le seuil SINR est satisfait. C'est le premier branchement concret du robust bitloading du papier.

## 12. Protection QoT des connexions existantes

`HypotheticalSlotAllocation` represente un slot candidat non encore reserve. Il permet de calculer l'impact d'un nouveau slot sur les allocations deja presentes, sans modifier l'etat du spectre.

`NetworkNliEvaluator` peut maintenant calculer une NLI robuste avec des allocations hypothetiques. L'interference d'un candidat est ajoutee seulement sur les liens communs et seulement dans les scenarios ou ce candidat serait actif.

`SpectrumState.uniqueAllocations` deduplique les allocations existantes, car le meme slot d'une connexion apparait sur plusieurs liens du chemin.

`ExistingConnectionsQoTChecker` implemente la verification centrale de l'algorithme 3: avant d'accepter un slot candidat, on recalcule la QoT robuste des slots existants en presence du candidat. Si `SINRmin` devient inferieur au seuil de la modulation deja assignee, le candidat doit etre rejete.

## 13. Greedy robust bitloading working

`GreedyRobustBitloading` implemente le debut du greedy optimal MF allocation de l'algorithme 3. Quand un nouveau slot working est teste, la classe recalcule la QoT robuste des slots deja choisis de la meme connexion en tenant compte du nouveau slot. Si une modulation deja choisie n'est plus supportee, elle est remplacee par la plus haute modulation encore admissible. Ensuite la modulation du nouveau slot est choisie comme la plus haute modulation dont le seuil SINR est satisfait.

`BitloadingDecision` transporte le resultat de cette operation: faisabilite, modulations mises a jour et debit porte apres eventuelles degradations.

`RobustCfssWorkingEvaluator` combine maintenant trois controles dans une version plus proche de `CFSSw`: disponibilite contigue working, preservation de la QoT des connexions existantes via `ExistingConnectionsQoTChecker`, puis greedy robust bitloading via `GreedyRobustBitloading`.

## 14. Greedy robust bitloading backup

`GreedyRobustBitloading` gere maintenant aussi l'ajout d'un slot backup. La difference essentielle avec le working est le choix des scenarios robustes: la QoT d'un backup est evaluee uniquement sous les pannes des liens du working path correspondant, car le backup n'est actif que dans ces cas.

`RobustCfssBackupEvaluator` est le pendant de `RobustCfssWorkingEvaluator` pour `CFSSb`. Il verifie la disponibilite backup libre ou partageable, protege la QoT des connexions existantes avec des allocations hypothetiques backup, puis applique le greedy robust bitloading backup.

## 15. Methode recursive de NLI pour les connexions existantes

`NliSnapshotStore` stocke les NLI deja calculees pour chaque allocation existante et chaque scenario robuste pertinent. La cle utilisee est `(connectionId, role, slotIndex, scenario)`.

`NetworkNliEvaluator.hypotheticalNliContributionW` calcule seulement la contribution XCI ajoutee par des allocations hypothetiques sur un slot existant, en respectant les liens communs et l'activite du candidat dans le scenario considere.

`RecursiveExistingConnectionsQoTChecker` reproduit l'idee recursive du papier: au lieu de recalculer toute la NLI d'un slot existant quand un nouveau slot est teste, il lit la NLI stockee, ajoute la contribution du candidat, puis reprend le maximum sur les scenarios pour obtenir `NLImax` et `SINRmin`.

## 16. Unification du garde QoT existant

`ExistingQoTGuard` est l'interface commune pour verifier qu'un ou plusieurs slots hypothetiques ne degradent pas les connexions existantes. `ExistingConnectionsQoTChecker` garde le chemin direct utile pour validation, tandis que `RecursiveExistingConnectionsQoTChecker` fournit le chemin principal base sur `NliSnapshotStore`.

`RobustCfssWorkingEvaluator` et `RobustCfssBackupEvaluator` dependent maintenant de `ExistingQoTGuard` au lieu d'une implementation concrete. Cela permet de brancher le checker recursif sans modifier la logique CFSS.

## 17. Rafraichissement dynamique du snapshot NLI

`NliSnapshotStore.refreshFromSpectrum` reconstruit les entrees NLI a partir de l'etat courant du spectre. Apres une allocation acceptee et reservee dans `SpectrumState`, ce rafraichissement permet au `RecursiveExistingConnectionsQoTChecker` de rester coherent avec les connexions etablies.

Cette premiere version privilegie la correction: elle reconstruit toutes les entrees du snapshot. Une optimisation ulterieure pourra remplacer ce rafraichissement complet par une mise a jour incrementale stricte `ancienne NLI + contribution du nouveau slot`, mais l'interface du snapshot est maintenant prete pour le cycle dynamique.

## 18. Tx/Rx partageables

`TransceiverState` modelise les ressources Tx/Rx par noeud. Une reservation working consomme des Tx/Rx exclusifs a la source et a la destination. Une reservation backup peut partager des Tx/Rx deja reserves en backup si les working paths des connexions concernees sont link-disjoint.

`TransceiverReservation` conserve les identifiants Tx/Rx choisis pour une connexion et un role. `TransceiverState` expose aussi `usedTransmitters`, `usedReceivers`, `txShareability` et `rxShareability`, ce qui prepare les metriques du papier et l'objectif multi-ressources.

`ObjectiveFunction` peut maintenant etre calculee directement a partir d'un `TransceiverState` et d'un `SpectrumState`.

`RmsaCoreProvisioner` peut recevoir un `TransceiverState`. Dans ce cas, chaque candidat working/backup est evalue sur une copie des ressources Tx/Rx: les candidats sans Tx/Rx disponibles sont ecartes et l'objectif utilise les vrais compteurs `usedTransmitters` et `usedReceivers`.

## 19. Mise a jour NLI incrementale apres allocation et liberation

`NliSnapshotStore.updateAfterAcceptedAllocation` ajoute la mise a jour recursive stricte demandee par le papier. Apres reservation d'une nouvelle allocation dans `SpectrumState`, les entrees deja presentes dans le snapshot sont mises a jour par `ancienne NLI + contribution XCI des nouveaux slots acceptes`. Les slots nouvellement acceptes sont, eux, calcules completement une fois afin d'initialiser leurs scenarios robustes.

`NliSnapshotStore.updateAfterReleasedAllocation` applique le pendant dynamique pour les departs: les entrees des slots liberes sont retirees du snapshot, puis leur contribution XCI est soustraite des allocations restantes. Cela donne le cycle recursif complet `ancienne NLI + contribution ajoutee` a l'arrivee et `ancienne NLI - contribution retiree` au depart.

Le processeur dynamique Net2Plan utilise maintenant ces deux mises a jour incrementales pour les arrivees acceptees et les departs. Le recalcul global `refreshFromSpectrum` reste disponible comme reference de validation.

`NliSnapshotIncrementalTest` compare explicitement les mises a jour incrementales d'acceptation et de liberation avec un recalcul global complet sur les allocations et scenarios robustes. Ce test verrouille l'equivalence scientifique avant optimisation plus fine.

## 20. Calibration physique explicite

`PhysicalLayerParameters` expose maintenant les constantes utilisees par le profil `paperLikeDefaults`: gamma, beta2, largeur de slot, attenuation, puissance signal, longueur de span, noise figure et frequence porteuse. Les valeurs directement retrouvees dans le papier sont verrouillees comme constantes du papier (`gamma = 1.33 W^-1 km^-1`, `beta2 = -21.7e-24 s^2/km`, `slotBandwidth = 37.5 GHz`). Les autres valeurs restent des hypotheses de simulation explicites et testables.

Des conversions controlees ont ete ajoutees pour dB/lineaire, dBm/W et dB/km vers attenuation lineaire. `PhysicalLayerCalibrationTest` verifie que le profil physique par defaut reste stable.

## 21. Tests scientifiques cibles

`ScientificCoreTargetedTest` separe plusieurs controles qui etaient auparavant noyes dans le smoke test general: monotonie XCI/SCI/ASE, selection des scenarios robustes working/backup, partage backup SBPP, equivalence du garde QoT direct et recursif, et degradation de modulation par le greedy robust bitloading.

La disjonction de chemins tient maintenant compte des liens bidirectionnels physiques: deux liens diriges opposes entre les memes noeuds sont consideres comme le meme risque physique. Cela impacte directement la validation working/backup link-disjoint et le partage backup entre connexions.

## 22. Scenarios robustes bidirectionnels dans le coeur

`FailureScenario` ne represente plus seulement un lien dirige tombe, mais un ensemble de liens diriges qui tombent ensemble. Le cas le plus important est le couple bidirectionnel `u->v` et `v->u`, considere comme un meme risque physique.

`SpectrumState` peut maintenant etre construit avec la liste des liens du reseau. Il en deduit les groupes de panne bidirectionnels par paire de noeuds non orientee. Les anciennes constructions par `linkCount` restent disponibles et creent des scenarios unitaires, utiles pour les tests minimaux.

`RobustScenarioGenerator`, `NetworkNliEvaluator`, `ExistingConnectionsQoTChecker`, `RecursiveExistingConnectionsQoTChecker` et `NliSnapshotStore` utilisent maintenant ces groupes de panne quand le spectre connait la topologie. Cela aligne le coeur scientifique avec la logique Net2Plan: une panne sur un lien physique active aussi le risque du lien retour.
