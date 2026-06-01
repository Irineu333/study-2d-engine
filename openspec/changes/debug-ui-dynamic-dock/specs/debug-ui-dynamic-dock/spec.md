## ADDED Requirements

### Requirement: Estado de atribuição de dock por painel
Cada painel de debug screen-space SHALL carregar um `defaultSlot` (constante,
definido pela classe do widget) e um estado de atribuição runtime que é **ou**
`DOCKED(currentSlot, orderInSlot)` **ou** `FLOATING(position)`. Painéis começam
`DOCKED` no `defaultSlot`. O `currentSlot` SHALL ser independente do `defaultSlot`:
mover um painel para outro slot altera o `currentSlot` sem perder o `defaultSlot`,
que permanece o alvo do reset.

#### Scenario: Painel inicia dockado no slot default
- **WHEN** um painel de debug é registrado sem nenhuma interação de arrasto
- **THEN** ele está `DOCKED` com `currentSlot` igual ao seu `defaultSlot`

#### Scenario: Mover para outro slot preserva o default
- **WHEN** um painel com `defaultSlot = TOP_LEFT` é arrastado e dockado em `TOP_RIGHT`
- **THEN** seu `currentSlot` passa a `TOP_RIGHT` e seu `defaultSlot` permanece `TOP_LEFT`

### Requirement: Ordem mutável dentro do slot
O `DebugDock` SHALL manter, para cada slot, uma lista ordenada dos painéis `DOCKED`
naquele `currentSlot`, e SHALL empilhá-los nessa ordem (não na ordem de registro/DFS).
A ordem SHALL ser editável: inserir um painel num `orderInSlot` reposiciona os demais
do slot de forma estável.

#### Scenario: Empilhamento segue a ordem do slot, não o registro
- **WHEN** dois painéis no mesmo slot têm `orderInSlot` que contraria a ordem de registro
- **THEN** o dock os empilha na ordem dada por `orderInSlot`

#### Scenario: Reordenar dentro do slot
- **WHEN** o usuário arrasta um painel para uma posição acima de outro painel do mesmo slot
- **THEN** ao soltar, o painel arrastado assume o `orderInSlot` daquela posição e o outro desloca

### Requirement: Regiões de tela para dock vs flutuar
O viewport SHALL ser mapeado em zonas de dock e zona livre. As bordas superior e
inferior SHALL formar faixas de dock de espessura definida (uma constante de tema),
e cada faixa SHALL ser fatiada em três terços horizontais correspondentes aos slots
`*_LEFT` / `*_CENTER` / `*_RIGHT` daquela borda. A região central restante (o miolo)
SHALL ser zona livre. Soltar um painel numa faixa o deixa `DOCKED`; soltar no miolo
o deixa `FLOATING`.

#### Scenario: Soltar numa faixa de borda docka
- **WHEN** o usuário solta um painel arrastado dentro da faixa superior, terço esquerdo
- **THEN** o painel fica `DOCKED` em `TOP_LEFT`

#### Scenario: Soltar no miolo flutua
- **WHEN** o usuário solta um painel arrastado no centro do viewport, longe das faixas
- **THEN** o painel fica `FLOATING` na posição em que foi solto

### Requirement: Resolução de drop target durante o arrasto
Enquanto um painel é arrastado, o subsistema SHALL computar a cada frame um drop
target a partir do **retângulo da janela arrastada** (não apenas da posição do
ponteiro/header): ou um par `(slot, índice de inserção)` quando uma borda da janela
alcança uma faixa de dock, ou nenhum (flutuar) quando a janela inteira está contida
no miolo. O magnetismo SHALL usar a borda superior da janela para a faixa de topo e
a inferior para a faixa de base — empurrar a janela contra uma borda a encaixa mesmo
com o ponteiro bem dentro do viewport; quando ambas as bordas alcançam faixas, a de
maior penetração vence. O terço do slot SHALL ser escolhido pelo centro horizontal da
janela. O índice de inserção dentro do slot SHALL ser derivado comparando a borda de
ataque da janela (topo para slots de topo, base para slots de base) com os painéis já
empilhados naquele slot. O painel sendo arrastado SHALL ser excluído do cálculo de
empilhamento do seu slot enquanto o arrasto dura, para não reservar espaço para si mesmo.

#### Scenario: Drop target acompanha a janela
- **WHEN** a janela arrastada deixa de tocar qualquer faixa de borda e fica contida no miolo
- **THEN** o drop target deixa de ser um `(slot, índice)` e passa a indicar flutuar

#### Scenario: Magnetismo considera a janela inteira, não o header
- **WHEN** a borda inferior da janela alcança a faixa de base enquanto o header (e o ponteiro) seguem no miolo
- **THEN** o drop target é `(slot de base, índice)` — a janela docka pela borda, não pelo header

### Requirement: Pull magnético consistente independente da ocupação
A força do magnetismo de um slot SHALL ser independente de quantos painéis já
estão dockados nele. Um slot vazio SHALL ter a faixa base (`dockBandThickness`)
medida da borda; um slot ocupado SHALL estender sua zona magnética para cobrir a
pilha ocupada **mais uma faixa inteira** de área de pouso além do fim da pilha, de
modo que a área de captura além da pilha tenha sempre ~uma faixa de espessura — o
segundo painel encaixa com a mesma folga que o primeiro teve, sem precisar invadir
os painéis já presentes.

#### Scenario: Captura consistente do segundo painel em diante
- **WHEN** um slot já tem um painel cujo corpo se estende bem além da faixa base, e uma segunda janela é arrastada para perto do fim dessa pilha (a até uma faixa de distância do fim, sem invadir o primeiro painel)
- **THEN** o drop target é `(aquele slot, índice ao fim da pilha)` — a zona de captura além da pilha é tão espessa quanto a faixa base

#### Scenario: Slot vazio mantém a faixa base
- **WHEN** o terço alvo não tem nenhum painel dockado
- **THEN** a zona magnética é a faixa base medida da borda da tela, inalterada

#### Scenario: Painel arrastado não reserva espaço para si
- **WHEN** um painel é arrastado dentro do próprio slot de origem
- **THEN** os demais painéis do slot fluem como se ele não ocupasse posição até soltar

### Requirement: Indicador de inserção visual
Enquanto há um drop target de dock, o subsistema SHALL desenhar um indicador de
inserção que comunica o slot alvo e a posição (índice) em que o painel cairá ao soltar.
Quando o drop target é flutuar (miolo), o indicador de inserção SHALL NOT ser desenhado.

#### Scenario: Indicador aparece sobre faixa de dock
- **WHEN** o ponteiro está sobre uma faixa de dock entre dois painéis durante o arrasto
- **THEN** um indicador de inserção é desenhado no gap correspondente àquela posição

#### Scenario: Sem indicador no miolo
- **WHEN** o ponteiro está no miolo (drop target = flutuar) durante o arrasto
- **THEN** nenhum indicador de inserção é desenhado
