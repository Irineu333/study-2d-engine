# debug-ui-window-controls Specification

## Purpose

Dar a cada painel de debug screen-space controles de janela no header — fechar
(soft close que apenas desabilita o widget, mantendo-o reabrível pela `DebugHud`)
e colapsar (esconde o corpo mantendo o header) — com visibilidade efetiva do
corpo (`enabled && !collapsed`), desmontagem dos corpos compostos por nós-filhos
ao colapsar, e estado de colapso vivo dentro da sessão. Gizmos world-space ficam
de fora: continuam controlados apenas pelo toggle da `DebugHud`.

## Requirements

### Requirement: Controle de fechar no header
O header de todo painel de debug screen-space (`ScreenDebugWidget`) SHALL exibir
um controle de fechar (`[x]`) no canto direito. Acioná-lo SHALL desabilitar o
widget (`enabled = false`), de modo que ele suma do dock e custe zero. O fechar
SHALL ser um soft close: o painel SHALL continuar reabrível pela `DebugHud` (e a
própria HUD pelo `debugHudKey`), sem dismiss permanente nem unregister.

#### Scenario: Fechar desabilita o widget
- **WHEN** o usuário aciona o controle de fechar no header de um painel
- **THEN** o widget passa a `enabled = false` e deixa de ser desenhado e de consumir cliques

#### Scenario: Painel fechado reabre pela HUD
- **WHEN** um painel foi fechado e o usuário marca sua linha na `DebugHud`
- **THEN** o widget volta a `enabled = true` e reaparece no dock

### Requirement: Controle de colapsar no header
O header de todo `ScreenDebugWidget` SHALL exibir um controle de colapsar (`[_]`)
no canto direito, à esquerda do controle de fechar. Acioná-lo SHALL alternar um
estado `collapsed`. Enquanto `collapsed`, o painel SHALL desenhar apenas o header
(mantendo título e controles) e SHALL NOT desenhar o corpo; ao expandir, o corpo
SHALL voltar.

#### Scenario: Colapsar esconde o corpo mantendo o header
- **WHEN** o usuário aciona o controle de colapsar de um painel expandido
- **THEN** o corpo deixa de ser desenhado, o header permanece visível, e a altura reportada cai para a altura do header

#### Scenario: Expandir restaura o corpo
- **WHEN** o usuário aciona o controle de colapsar de um painel já colapsado
- **THEN** o corpo volta a ser desenhado e a altura reportada volta a incluir o corpo

### Requirement: Visibilidade efetiva do corpo
A base `ScreenDebugWidget` SHALL expor a visibilidade efetiva do corpo como
`enabled && !collapsed`. O chrome (header) SHALL ser desenhado sempre que
`enabled`; o corpo (`drawDebug`) SHALL ser desenhado apenas quando a visibilidade
efetiva for verdadeira. O tamanho reportado ao `DebugDock` SHALL incluir o corpo
apenas quando a visibilidade efetiva for verdadeira, de modo que o dock re-flua os
demais painéis ao colapsar/expandir.

#### Scenario: Dock re-flui ao colapsar
- **WHEN** um painel no mesmo slot de outro é colapsado
- **THEN** o dock re-empilha os painéis usando a altura reduzida do painel colapsado

#### Scenario: Corpo não desenha enquanto colapsado
- **WHEN** um painel está colapsado
- **THEN** `drawDebug` não é chamado, embora o chrome continue sendo desenhado

### Requirement: Colapso desmonta corpos de nós-filhos
Widgets de corpo composto por nós-filhos MUST observar a visibilidade efetiva do
corpo, e não apenas `enabled`, para montar e desmontar esses filhos — caso de
`DebugHud` e `TimeControlWidget`. Ao colapsar, esses filhos MUST ser desmontados,
e não apenas ocultados, de modo a não desenhar nem receber hit-test.

#### Scenario: Painel de nós-filhos colapsado não consome clique no corpo
- **WHEN** um painel cujo corpo são botões é colapsado e o usuário clica onde o corpo estaria
- **THEN** nenhum botão é atingido, porque os filhos foram desmontados

#### Scenario: Corpo de nós-filhos remonta ao expandir
- **WHEN** um painel de nós-filhos colapsado é expandido
- **THEN** os botões do corpo são remontados e voltam a desenhar e receber clique

### Requirement: Estado de colapso na sessão
O estado `collapsed` de um painel SHALL sobreviver ao toggle on/off (`enabled`)
do widget e ao `tree.resize` dentro da mesma execução. O estado `collapsed`
SHALL NOT persistir entre execuções.

#### Scenario: Colapso sobrevive ao toggle
- **WHEN** um painel colapsado é desabilitado e reabilitado
- **THEN** ele reaparece colapsado, mostrando só o header

### Requirement: Controles só em painéis screen-space
Os controles de fechar e colapsar SHALL existir apenas em painéis screen-space
(`ScreenDebugWidget`), que possuem chrome/header. Gizmos world-space
(`WorldDebugWidget`) SHALL NOT exibir esses controles e SHALL permanecer
controlados apenas pelo toggle da `DebugHud`.

#### Scenario: Gizmo não tem controles de janela
- **WHEN** um `WorldDebugWidget` está habilitado
- **THEN** ele não desenha header nem controles de fechar/colapsar
