# Integration multi-domaine - contribution

## Base

Projet clone depuis `Robust_SBPP_NLIA_RMSA_Contribution`.
Le coeur scientifique `rmsa.core` reste la source de verite pour :

- CFSS working/backup ;
- SBPP et partage de backup ;
- bitloading robuste ;
- QoT/SINR/NLI ;
- scenarios robustes ;
- objectif Tx/Rx + occupation spectrale.

La couche multi-domaine ne doit pas remplacer ce coeur. Elle doit seulement orchestrer la generation de candidats.

## Ce qui est repris de `MultiDomain_SBPP_CFSS_improved`

Idees retenues :

- broker multi-domaine ;
- topologie agregee par domaines ;
- controleurs locaux ;
- chemins de domaines ;
- distinction intra-domaine / inter-domaine ;
- preference pour un backup de domaine different quand possible.

Elements non recopies tels quels :

- ancien allocateur local par segment ;
- ancienne reservation transactionnelle locale ;
- ancien moteur QoT simplifie ;
- ancien ProtectionManager complet.

Raison : le nouveau coeur scientifique est plus conforme au papier. Le broker doit donc alimenter ce coeur, pas le contourner.

## Premiere brique implementee

Ajout du package `rmsa.net2plan.multidomain` :

- `DomainPath` : chemin abstrait de domaines ;
- `LocalDomainController` : facade locale minimale ;
- `MultiDomainBroker` : lecture des domaines Net2Plan, calcul des chemins de domaines working/backup.

Ajout de `Net2PlanMultiDomainCandidatePathBuilder` :

- lit les attributs de noeuds `domainId`, `domain` ou `domainName` ;
- si la demande est intra-domaine, conserve le comportement existant ;
- si la demande est inter-domaine, calcule des chemins de domaines ;
- genere des chemins physiques contraints par le chemin de domaines ;
- genere des backups link-disjoint ;
- envoie les paires working/backup au coeur robuste existant.

Le processeur Net2Plan clone utilise maintenant ce builder multi-domaine.

## Prochaines briques

1. Fait partiellement : metriques intra/inter-domaine et transitions acceptees dans le rapport.`r`n2. Fait : attributs de routes `domainPath`, `interDomain`, `domainTransitions`.`r`n3. Ajouter une trace detaillee broker/controller dans le fichier scientifique.
4. Ajouter une politique plus forte de backup de domaine : disjonction de transitions inter-domaines, puis fallback si impossible.
5. Ajouter des tests smoke sur une topologie 2 ou 3 domaines.
6. Ajouter un parametre pour forcer ou non le mode multi-domaine si besoin experimental.