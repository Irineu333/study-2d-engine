## MODIFIED Requirements

### Requirement: Arrasto de painel de debug
Um painel de debug screen-space SHALL ser arrastável: pegar sua zona de pega
(o header, exceto os retângulos interativos) e mover o ponteiro reposiciona o
painel, soltando ao liberar o botão. O grip (afordância 2×3 de pontos) SHALL ser
desenhado à esquerda do título; os controles de janela (colapsar/fechar) SHALL
ser desenhados no canto direito. A zona de pega SHALL recortar os retângulos do
grip e dos controles, de modo que pressioná-los não inicie um arrasto. O arrasto
SHALL usar polling de `Input` (`isMouseDown` + `pointerPosition`), sem depender de
um modelo de eventos.

#### Scenario: Arrastar reposiciona o painel
- **WHEN** o usuário pressiona a zona de pega de um painel de debug e move o ponteiro
- **THEN** o painel segue o ponteiro mantendo o offset de pega, até o botão ser solto

#### Scenario: Clique de conteúdo não é arrasto
- **WHEN** o usuário clica um botão dentro de um painel de debug (fora da zona de pega)
- **THEN** o clique é roteado ao botão normalmente, sem iniciar um arrasto

#### Scenario: Clique num controle do header não é arrasto
- **WHEN** o usuário pressiona o controle de colapsar ou de fechar no header
- **THEN** a ação do controle é executada e nenhum arrasto é iniciado

### Requirement: Reset ao slot default
O subsistema SHALL oferecer um gesto para devolver um painel (e uma variante para
todos) ao layout default do dock. O gesto SHALL limpar o override de posição e
SHALL expandir o painel (limpar o estado `collapsed`), restaurando posição e
corpo numa única ação.

#### Scenario: Reset devolve o painel ao slot
- **WHEN** o usuário aciona o reset de um painel arrastado
- **THEN** o override é limpo e o painel volta a ser posicionado pelo dock no seu slot

#### Scenario: Reset expande painéis colapsados
- **WHEN** o usuário aciona o reset com um ou mais painéis colapsados
- **THEN** esses painéis voltam a expandir, mostrando o corpo novamente
