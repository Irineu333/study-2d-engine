# debug-ui-draggable Specification

## Purpose

Tornar os painéis de debug screen-space arrastáveis pelo usuário: pegar a zona
de pega (topo/título) reposiciona o painel via polling de `Input`, com consumo
de arrasto para não vazar ao gameplay, terminação por drop target (dockar num
slot ou flutuar no miolo), gesto de reset ao layout default e memória de posição
dentro da sessão.

## Requirements

### Requirement: Arrasto de painel de debug
Um painel de debug screen-space SHALL ser arrastável: pegar sua zona de pega
(o header, exceto os retângulos interativos) e mover o ponteiro reposiciona o
painel, soltando ao liberar o botão. O grip (afordância 2×3 de pontos) SHALL ser
desenhado à esquerda do título; os controles de janela (colapsar/fechar) SHALL
ser desenhados no canto direito. A zona de pega SHALL recortar os retângulos do
grip e dos controles, de modo que pressioná-los não inicie um arrasto. O arrasto
SHALL usar polling de `Input` (`isMouseDown` + `pointerPosition`), sem depender de
um modelo de eventos. Ao soltar, o arrasto SHALL **resolver um drop target**: se o
ponteiro está sobre uma faixa de dock, o painel é dockado no `(slot, índice)`
resolvido (re-dock ou reordenação); se está no miolo, o painel passa a flutuar na
posição em que foi solto.

#### Scenario: Arrastar reposiciona o painel
- **WHEN** o usuário pressiona a zona de pega de um painel de debug e move o ponteiro
- **THEN** o painel segue o ponteiro mantendo o offset de pega, até o botão ser solto

#### Scenario: Clique de conteúdo não é arrasto
- **WHEN** o usuário clica um botão dentro de um painel de debug (fora da zona de pega)
- **THEN** o clique é roteado ao botão normalmente, sem iniciar um arrasto

#### Scenario: Clique num controle do header não é arrasto
- **WHEN** o usuário pressiona o controle de colapsar ou de fechar no header
- **THEN** a ação do controle é executada e nenhum arrasto é iniciado

#### Scenario: Soltar sobre faixa de dock encaixa o painel
- **WHEN** o usuário solta um painel arrastado sobre uma faixa de dock
- **THEN** o painel fica dockado no slot e índice resolvidos, sem ficar flutuante

#### Scenario: Soltar no miolo deixa o painel flutuante
- **WHEN** o usuário solta um painel arrastado no miolo do viewport
- **THEN** o painel passa ao estado flutuante na posição em que foi solto

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

### Requirement: Estado flutuante explícito sobre o dock
Um painel SHALL estar em exatamente um de dois estados de posição: dockado num
slot (posicionado pelo `DebugDock`) ou flutuante (posição livre que sobrepõe
qualquer slot). O estado flutuante SHALL ser entrado **apenas** ao soltar um arrasto
no miolo do viewport — não é mais o resultado default de qualquer arrasto. Enquanto
flutuante, o painel SHALL ser desenhado na sua posição livre. O `DebugDock` SHALL
empilhar num slot apenas os painéis dockados naquele slot, ignorando os flutuantes.

#### Scenario: Painel flutuante vence o slot
- **WHEN** um painel está no estado flutuante
- **THEN** ele é desenhado na sua posição livre, não no origin de nenhum slot

#### Scenario: Painéis dockados continuam fluindo no slot
- **WHEN** um painel de um slot é arrastado para o miolo (flutua) e outro do mesmo slot não
- **THEN** o dock re-empilha o painel dockado sem reservar espaço para o flutuante

#### Scenario: Arrasto não vira flutuante por padrão
- **WHEN** o usuário arrasta um painel e solta sobre uma faixa de dock
- **THEN** o painel permanece dockado e não entra no estado flutuante

### Requirement: Reset ao layout default
O subsistema SHALL oferecer um gesto para devolver um painel (e uma variante para
todos) ao layout default do dock. O gesto SHALL devolver o painel ao seu `defaultSlot`
com a ordem default do slot, SHALL limpar o estado flutuante (caso esteja flutuante),
e SHALL expandir o painel (limpar o estado `collapsed`), restaurando slot, ordem e
corpo numa única ação.

#### Scenario: Reset devolve o painel ao slot default
- **WHEN** o usuário aciona o reset de um painel que foi movido para outro slot ou está flutuante
- **THEN** o painel volta ao seu `defaultSlot` com a ordem default, posicionado pelo dock

#### Scenario: Reset expande painéis colapsados
- **WHEN** o usuário aciona o reset com um ou mais painéis colapsados
- **THEN** esses painéis voltam a expandir, mostrando o corpo novamente

### Requirement: Memória de posição na sessão
O estado de posição de um painel (slot/ordem dockados ou posição flutuante) SHALL
sobreviver ao toggle on/off do widget e ao `tree.resize` dentro da mesma execução;
uma posição flutuante SHALL ser re-clampada para dentro do viewport quando este
encolhe. O estado de posição SHALL NOT persistir entre execuções.

#### Scenario: Posição sobrevive ao toggle
- **WHEN** um painel movido ou flutuante é desabilitado e reabilitado
- **THEN** ele reaparece no mesmo slot/ordem ou posição flutuante, não no layout default

#### Scenario: Re-clamp no resize
- **WHEN** o `tree.size` encolhe a ponto de a posição flutuante cair fora do viewport
- **THEN** o painel é re-clampado para dentro do viewport, permanecendo visível
