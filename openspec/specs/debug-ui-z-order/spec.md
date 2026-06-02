# debug-ui-z-order Specification

## Purpose

Dar aos painéis de debug screen-space (`ScreenDebugWidget`) uma ordem de
empilhamento (z-order) determinística e interativa: painéis sobrepostos pintam
um inequivocamente por cima do outro segundo a ordem dos filhos do
`ScreenDebugCanvas`, qualquer press traz o painel à frente, e exatamente um
painel é eleito dono do press no overlap para que só ele arme arrasto. O z-order
é estado de sessão, persistido através de `resize` e toggle de habilitação.

## Requirements

### Requirement: Z-order entre painéis de debug sobrepostos

A engine SHALL pintar painéis de debug screen-space (`ScreenDebugWidget`) sobrepostos segundo uma ordem de empilhamento (z-order) determinística, um inequivocamente por cima do outro. O z-order SHALL ser
materializado pela ordem dos filhos do `ScreenDebugCanvas`: um painel mais ao
fim da lista de filhos pinta por cima de um mais ao início, coerente com a
ordem de pintura do UI pass (DFS, último por cima). Painéis docked nunca se
sobrepõem (o `DebugDock` os posiciona por `orderInSlot`), de modo que o z-order
SHALL ser observável apenas entre painéis flutuantes ou entre painéis de slots
distintos cujas áreas se cruzem.

#### Scenario: Painel flutuante por cima cobre o de baixo

- **GIVEN** dois painéis de debug flutuantes A e B cujas áreas se sobrepõem, com B mais ao fim da lista de filhos do `ScreenDebugCanvas`
- **WHEN** o UI pass desenha o `ScreenDebugCanvas`
- **THEN** B SHALL ser desenhado por cima de A na região de sobreposição

#### Scenario: Layout dockado é indiferente ao z-order

- **WHEN** a ordem dos filhos do `ScreenDebugCanvas` muda
- **THEN** a posição de cada painel docked permanece a determinada pelo `DebugDock` a partir de `currentSlot` e `orderInSlot`, sem deslocamento

### Requirement: Bring-to-front em qualquer press

Um press em qualquer ponto de um painel de debug habilitado SHALL trazer esse
painel à frente dos demais painéis de debug — seja o ponto no header, no corpo,
num window control (close/collapse) ou num `Button` interno —, movendo-o para o
fim da lista de filhos do `ScreenDebugCanvas`. O bring-to-front SHALL ser
aplicado uniformemente a todos os painéis, sem caso especial para docked ou
flutuante; em painéis docked o efeito é visualmente inócuo por não haver
sobreposição. O bring-to-front SHALL ocorrer no pré-passe `SceneTree.hitTestUI`,
antes de `tree.process`, de modo que o painel já pinte no topo no mesmo frame e
o gesto de arrasto subsequente ocorra sobre o painel à frente. O bring-to-front
SHALL ser independente de quem absorve o clique: um press sobre um `Button` ou
window control do painel ainda o traz à frente, e o controle executa sua ação
normalmente.

#### Scenario: Press traz painel coberto para frente

- **GIVEN** dois painéis flutuantes A (por cima) e B (por baixo) sobrepostos
- **WHEN** o usuário pressiona uma região de B que não está coberta por A
- **THEN** B SHALL passar a pintar por cima de A a partir desse frame

#### Scenario: Press no corpo também traz para frente

- **WHEN** o usuário pressiona o corpo (não o header) de um painel coberto por outro
- **THEN** o painel pressionado SHALL ser trazido à frente, ainda que nenhum arrasto seja iniciado

#### Scenario: Press num Button do painel também traz para frente

- **WHEN** o usuário pressiona um `Button` interno de um painel de debug
- **THEN** o painel SHALL ser trazido à frente e o `Button` SHALL emitir `pressed` normalmente, sem iniciar arrasto

### Requirement: Dono de press único no overlap

A engine SHALL eleger exatamente um painel como dono do press quando um press cai sobre a sobreposição de dois ou mais painéis de debug — o painel no topo do z-order sob o ponteiro (resolvido top-most-first no `hitTestUI`). Somente o
painel dono SHALL armar seu arrasto por polling em
`ScreenDebugWidget.updateDrag`; os demais painéis sob o ponteiro SHALL ficar
inertes para aquele press. Isto SHALL substituir a leitura do clique cru por
cada painel, evitando que um press no overlap arme arrasto em mais de um painel.

#### Scenario: Press no overlap arma só o painel do topo

- **GIVEN** dois painéis flutuantes sobrepostos
- **WHEN** o usuário pressiona o header na região de sobreposição
- **THEN** apenas o painel do topo SHALL iniciar o arrasto; o painel de baixo não inicia arrasto

#### Scenario: Press num controle do painel do topo não vaza para o de baixo

- **WHEN** o usuário pressiona o controle de fechar de um painel do topo sobreposto a outro
- **THEN** apenas o painel do topo executa a ação de fechar; o de baixo não reage ao press

### Requirement: Persistência do z-order na sessão

O z-order dos painéis SHALL persistir dentro da sessão através de `tree.resize`
e do toggle enable/disable de um painel. Como a ordem é a da lista de filhos do
`ScreenDebugCanvas`, que não é reconstruída nesses eventos, um painel trazido à
frente SHALL permanecer à frente após um resize do viewport ou após ser
desabilitado e reabilitado, sem reordenação implícita. O z-order é estado de
sessão apenas e SHALL NOT ser persistido em disco.

#### Scenario: Z-order sobrevive ao resize

- **GIVEN** o painel B foi trazido à frente do painel A
- **WHEN** o viewport é redimensionado (`tree.resize`)
- **THEN** B SHALL continuar pintando por cima de A

#### Scenario: Z-order sobrevive ao toggle de habilitação

- **GIVEN** o painel B está à frente do painel A
- **WHEN** B é desabilitado e reabilitado
- **THEN** B SHALL reter sua posição na lista de filhos relativa a A
