# debug-ui-draggable

## ADDED Requirements

### Requirement: Arrasto de painel de debug
Um painel de debug screen-space SHALL ser arrastável: pegar sua zona de pega
(topo/título) e mover o ponteiro reposiciona o painel, soltando ao liberar o
botão. O arrasto SHALL usar polling de `Input` (`isMouseDown` +
`pointerPosition`), sem depender de um modelo de eventos.

#### Scenario: Arrastar reposiciona o painel
- **WHEN** o usuário pressiona a zona de pega de um painel de debug e move o ponteiro
- **THEN** o painel segue o ponteiro mantendo o offset de pega, até o botão ser solto

#### Scenario: Clique de conteúdo não é arrasto
- **WHEN** o usuário clica um botão dentro de um painel de debug (fora da zona de pega)
- **THEN** o clique é roteado ao botão normalmente, sem iniciar um arrasto

### Requirement: Consumo de arrasto
Quando um painel de debug está sendo arrastado, o `Input` SHALL sinalizar o
arrasto como consumido (espelhando `mouseClickConsumed`), e consumidores de
arraste de gameplay SHALL respeitar esse sinal, de modo que arrastar o painel
não arraste a câmera nem o mundo. O sinal SHALL ser resetado a cada tick.

#### Scenario: Arrasto de painel não vaza para o gameplay
- **WHEN** um painel de debug está sendo arrastado
- **THEN** um consumidor de arraste de gameplay que respeita o sinal não recebe o arrasto

#### Scenario: Sinal reseta por tick
- **WHEN** o arrasto do painel termina
- **THEN** no tick seguinte o sinal de arrasto-consumido está limpo

### Requirement: Override de posição sobre o slot
Um painel arrastado SHALL guardar uma posição custom que sobrepõe o origin que
o `DebugDock` daria pelo seu `DockSlot`. Enquanto sem override, o painel SHALL
seguir o slot. O `DebugDock` SHALL empilhar no slot apenas os painéis sem override.

#### Scenario: Override vence o slot
- **WHEN** um painel tem posição custom (foi arrastado)
- **THEN** ele é desenhado na posição custom, não no origin do slot

#### Scenario: Painéis sem override continuam fluindo no slot
- **WHEN** um painel de um slot é arrastado para fora e outro do mesmo slot não
- **THEN** o dock re-empilha o painel sem override sem reservar espaço para o arrastado

### Requirement: Reset ao slot default
O subsistema SHALL oferecer um gesto para limpar o override de posição de um
painel (e uma variante para todos), devolvendo-o ao layout default do dock.

#### Scenario: Reset devolve o painel ao slot
- **WHEN** o usuário aciona o reset de um painel arrastado
- **THEN** o override é limpo e o painel volta a ser posicionado pelo dock no seu slot

### Requirement: Memória de posição na sessão
A posição custom de um painel SHALL sobreviver ao toggle on/off do widget e ao
`tree.resize` dentro da mesma execução, sendo re-clampada para dentro do
viewport quando este encolhe. A posição custom SHALL NOT persistir entre execuções.

#### Scenario: Posição sobrevive ao toggle
- **WHEN** um painel arrastado é desabilitado e reabilitado
- **THEN** ele reaparece na posição custom, não no slot

#### Scenario: Re-clamp no resize
- **WHEN** o `tree.size` encolhe a ponto de a posição custom cair fora do viewport
- **THEN** o painel é re-clampado para dentro do viewport, permanecendo visível
