## MODIFIED Requirements

### Requirement: Controle de fechar no header
O header de todo painel de debug screen-space (`ScreenDebugWidget`) SHALL exibir
um controle de fechar (`[x]`) no canto direito **a menos que o painel declare
`closable = false`**. Quando exibido, acioná-lo SHALL desabilitar o widget
(`enabled = false`), de modo que ele suma do dock e custe zero; o fechar SHALL
ser um soft close — o painel SHALL continuar reabrível pela `DebugHud` (e a
própria HUD pelo `debugHudKey`), sem dismiss permanente nem unregister. Quando o
painel declara `closable = false`, o header SHALL NOT desenhar nem hit-testar o
controle de fechar, e o painel só pode ser desligado por quem governa seu
`enabled` (caso do painel escravo do Inspector, cujo `enabled` deriva do mestre).
A propriedade `closable` SHALL ter default `true`, preservando o comportamento de
todos os painéis existentes.

#### Scenario: Fechar desabilita o widget
- **WHEN** o usuário aciona o controle de fechar no header de um painel `closable`
- **THEN** o widget passa a `enabled = false` e deixa de ser desenhado e de consumir cliques

#### Scenario: Painel fechado reabre pela HUD
- **WHEN** um painel foi fechado e o usuário marca sua linha na `DebugHud`
- **THEN** o widget volta a `enabled = true` e reaparece no dock

#### Scenario: Painel não-closable não desenha o controle de fechar
- **WHEN** um `ScreenDebugWidget` declara `closable = false` e está habilitado
- **THEN** o header não desenha o `[x]` e um clique onde ele estaria não desabilita o painel

### Requirement: Controle de colapsar no header
O header de todo `ScreenDebugWidget` SHALL exibir um controle de colapsar (`[_]`)
no canto direito, à esquerda do controle de fechar, **a menos que o painel
declare `collapsible = false`**. Quando exibido, acioná-lo SHALL alternar um
estado `collapsed`; enquanto `collapsed`, o painel SHALL desenhar apenas o header
(mantendo título e controles disponíveis) e SHALL NOT desenhar o corpo; ao
expandir, o corpo SHALL voltar. Quando o painel declara `collapsible = false`, o
header SHALL NOT desenhar nem hit-testar o controle de colapsar e o painel
permanece sempre expandido. A propriedade `collapsible` SHALL ter default
`true`, preservando o comportamento de todos os painéis existentes.

#### Scenario: Colapsar esconde o corpo mantendo o header
- **WHEN** o usuário aciona o controle de colapsar de um painel `collapsible` expandido
- **THEN** o corpo deixa de ser desenhado, o header permanece visível, e a altura reportada cai para a altura do header

#### Scenario: Expandir restaura o corpo
- **WHEN** o usuário aciona o controle de colapsar de um painel já colapsado
- **THEN** o corpo volta a ser desenhado e a altura reportada volta a incluir o corpo

#### Scenario: Painel não-collapsible não desenha o controle de colapsar
- **WHEN** um `ScreenDebugWidget` declara `collapsible = false` e está habilitado
- **THEN** o header não desenha o `[_]` e um clique onde ele estaria não colapsa o painel
