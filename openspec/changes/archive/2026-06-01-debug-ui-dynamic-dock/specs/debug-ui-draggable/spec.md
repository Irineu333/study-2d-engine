## MODIFIED Requirements

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
