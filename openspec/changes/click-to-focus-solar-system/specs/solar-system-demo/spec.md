## ADDED Requirements

### Requirement: Clicking a celestial body focuses the camera on it

A demo `Transforms` SHALL permitir que o usuário **trave** a câmera num corpo celeste (`Sun`, qualquer planeta ou qualquer lua — todos `Circle2D`) clicando-o com o botão esquerdo do mouse. Enquanto um corpo está focado, `SolarSystemDemo` MUST manter `focused: Node2D?` apontando para aquele `Circle2D` (e `null` quando nada está focado). A seleção MUST usar um teste de distância próprio (NÃO `SceneTree.hitTestPick`): converter `input.pointerPosition` para world via `cam.screenToWorld(pointer, tree.size)` e, entre os corpos cujo `world().position` está a no máximo `max(corpo.radius, MIN_PICK_PX / escalaDaCamera)` do ponto clicado, escolher o de **menor raio** (a lua sobre o planeta, o corpo mais à frente). O piso em pixels de tela (`MIN_PICK_PX`) MUST garantir que luas minúsculas (raio ~2px) sejam clicáveis em qualquer nível de zoom.

#### Scenario: Clicking a planet focuses it

- **GIVEN** a demo `Transforms` está ativa e nenhum corpo está focado
- **WHEN** o usuário clica (botão esquerdo, sem arrastar) sobre um planeta
- **THEN** `focused` passa a referenciar o `Circle2D` daquele planeta

#### Scenario: Smallest body under the cursor wins

- **WHEN** o ponto clicado (em world) está dentro do raio de pick de um planeta e de uma de suas luas simultaneamente
- **THEN** a lua (corpo de menor raio) é a escolhida como `focused`

#### Scenario: Tiny moons are clickable via screen-space pick floor

- **GIVEN** um corpo de raio ~2px renderizado num zoom em que 2px de mundo é muito menor que o cursor
- **WHEN** o usuário clica praticamente sobre o corpo
- **THEN** o pick o seleciona, porque o raio efetivo de pick é `max(corpo.radius, MIN_PICK_PX / escalaDaCamera)`

### Requirement: Focused camera zooms in to frame the body and its orbit

Enquanto `focused != null`, `SolarSystemDemo` SHALL convergir `Camera2D.bounds.size` para um tamanho de foco por **suavização exponencial** a cada `onProcess` (`size += (focusSize − size) * min(1f, FOCUS_LERP * dt)`), em vez de saltar instantaneamente. O `focusSize` MUST enquadrar o corpo **e sua vizinhança orbital**: a meia-extensão considerada MUST ser `max(corpo.radius * FOCUS_RADIUS_MULT, maiorRaioOrbitalDeFilho, FOCUS_MIN_HALF)` mais um padding, de modo que focar um planeta com luas enquadre também as órbitas das luas. O piso de largura de foco MUST poder ser **menor** que o `MIN_ZOOM_WIDTH` do modo livre (clamp de zoom relaxado em modo travado) para que luas pequenas sejam enquadráveis. O aspecto MUST ser preservado (reusar o `clampZoom` existente).

#### Scenario: Zoom converges smoothly, not instantly

- **GIVEN** a câmera está num zoom-out amplo e o usuário foca um corpo
- **WHEN** alguns frames de `onProcess(dt)` decorrem
- **THEN** `bounds.size` diminui progressivamente em direção ao `focusSize` (lerp exponencial), aproximando-se a cada frame sem salto único

#### Scenario: Focus framing includes child orbits

- **WHEN** o corpo focado tem luas (ex.: `Jupiter`)
- **THEN** o `focusSize` convergido enquadra o planeta e o maior raio orbital de suas luas (a meia-extensão considera `maiorRaioOrbitalDeFilho`)

### Requirement: Focused camera follows the body every frame

Enquanto `focused != null`, `SolarSystemDemo` SHALL recentrar `Camera2D.bounds.origin` no corpo a cada `onProcess`, computando `origin = focused.world().position − bounds.size/2`, de modo que o corpo permaneça no centro do viewport enquanto a hierarquia aninhada gira em volta. O recenter MUST ler `world().position` (que compõe toda a cadeia de transforms ancestrais), demonstrando o invariante A1 — seguir uma lua faz o Sol e o planeta-mãe orbitarem em torno dela na tela.

#### Scenario: Focused body stays centered while the scene rotates

- **GIVEN** um corpo está focado
- **WHEN** os `Rotator`s avançam e o `onProcess` recentra a câmera
- **THEN** `bounds.origin + bounds.size/2` é igual a `focused.world().position` (o corpo fica centrado)
- **AND** os demais corpos aparentam girar em torno do centro

#### Scenario: Following a moon orbits the universe around it

- **GIVEN** uma lua (ex.: `Moon` sob `Earth`) está focada
- **WHEN** o tempo avança
- **THEN** a lua permanece centrada e o planeta-mãe + o Sol descrevem trajetórias em torno do centro da tela

### Requirement: Left click disambiguates select from drag-pan

A demo `Transforms` SHALL distinguir, no botão esquerdo, um **clique** (seleção de corpo) de um **arrasto** (pan), sem alterar o drag-pan existente. Como `Input.wasMouseClicked` é press-edge, a disambiguação MUST rastrear a borda de `isMouseDown(Left)`: gravar `pressAnchor` no press; se o ponteiro se mover além de `CLICK_SLOP_PX` em relação ao `pressAnchor` enquanto o botão está pressionado, a interação MUST ser tratada como arrasto (pan) e não como clique; se o botão for solto sem ter cruzado o slop, a interação MUST ser tratada como clique (executa o pick). O drag-pan MUST continuar honrando `mouseDragConsumed`.

#### Scenario: Stationary press-release selects

- **WHEN** o usuário pressiona e solta o botão esquerdo sobre um corpo sem mover além de `CLICK_SLOP_PX`
- **THEN** a interação é um clique e o pick é executado

#### Scenario: Press and drag pans without selecting

- **WHEN** o usuário pressiona o botão esquerdo e move além de `CLICK_SLOP_PX` antes de soltar
- **THEN** a interação é um arrasto: o pan acontece (comportamento atual) e nenhum corpo é selecionado pelo soltar

### Requirement: Locked mode is fluid — scroll adjusts focus, gestures release

Enquanto `focused != null`, a demo `Transforms` SHALL operar em **modo fluido**: o scroll do mouse MUST ajustar o nível de zoom de foco (escalar o `focusSize`-alvo, clampeado) mantendo o corpo centrado, em vez de mover `bounds` livremente. A demo MUST **destravar** (`focused = null`, devolvendo o controle livre de scroll/drag/setas na view corrente, sem restaurar `bounds`) quando ocorrer qualquer um: o arrasto cruzar `CLICK_SLOP_PX`; a tecla `Esc` for pressionada; um clique cair no vazio (pick não encontra corpo); ou um clique acertar o **mesmo** corpo já focado (toggle). Quando `focused == null`, o comportamento livre da câmera (scroll-zoom em torno do cursor, drag-pan grab-and-drag, pan por setas) MUST ser idêntico ao anterior a esta change.

#### Scenario: Scroll while focused adjusts focus zoom, keeps body centered

- **GIVEN** um corpo está focado
- **WHEN** o usuário rola o scroll
- **THEN** o zoom de foco muda (o `focusSize`-alvo é escalado) e o corpo permanece centrado (a câmera não faz pan livre)

#### Scenario: Escape releases focus

- **GIVEN** um corpo está focado
- **WHEN** o usuário pressiona `Esc`
- **THEN** `focused` volta a `null` e o controle livre da câmera é restabelecido na view corrente

#### Scenario: Clicking empty space releases focus

- **GIVEN** um corpo está focado
- **WHEN** o usuário clica numa região sem nenhum corpo dentro do raio de pick
- **THEN** `focused` volta a `null`

#### Scenario: Clicking the focused body toggles focus off

- **GIVEN** um corpo está focado
- **WHEN** o usuário clica novamente nesse mesmo corpo
- **THEN** `focused` volta a `null`

#### Scenario: Free camera unchanged when nothing is focused

- **GIVEN** nenhum corpo está focado
- **WHEN** o usuário usa scroll, arrasto ou setas
- **THEN** o zoom/pan se comporta exatamente como antes desta change

### Requirement: Focused body has visual feedback (ring + HUD name)

Enquanto `focused != null`, a demo `Transforms` SHALL prover feedback visual do corpo focado: (a) um **anel de seleção** desenhado em world-space no `onDraw` da demo — um círculo não-preenchido centrado em `focused.world().position` com raio `focused.radius + RING_GAP` — e (b) o **nome** do corpo focado exibido no overlay de UI da demo via um `Label` em `CanvasLayer` (screen-space, imune à view transform da câmera, invariante #6). Ambos MUST refletir o mesmo campo `focused` e MUST sumir quando `focused == null`. O anel MUST ser desenhado em `onDraw` (invariante #4 — nada desenha fora de `SceneTree.render`).

#### Scenario: Ring is drawn around the focused body

- **GIVEN** um corpo está focado
- **WHEN** o `onDraw` da demo executa
- **THEN** um círculo não-preenchido é desenhado em world-space em `focused.world().position` envolvendo o corpo

#### Scenario: HUD shows the focused body name in a CanvasLayer

- **GIVEN** um corpo chamado `Europa` está focado
- **WHEN** o overlay da demo é renderizado
- **THEN** um `Label` em `CanvasLayer` exibe o nome do corpo focado
- **AND** ao aplicar zoom/pan o `Label` não escala nem translada (permanece em screen-space)

#### Scenario: Feedback disappears when focus is released

- **WHEN** `focused` volta a `null`
- **THEN** o anel de seleção não é mais desenhado
- **AND** o `Label` de nome do corpo focado fica vazio/oculto

### Requirement: Arrow keys switch focus directionally while locked

Enquanto `focused != null`, a demo `Transforms` SHALL permitir trocar o corpo focado para outro corpo via **setas direcionais**, navegando espacialmente: cada seta (←/→/↑/↓) MUST selecionar o corpo mais próximo na direção correspondente (em world-space, com `y` crescente para baixo) a partir do `world().position` do corpo atualmente focado. A troca MUST ocorrer na **borda de pressionar** (`wasKeyPressed`), uma troca por toque. Apenas corpos cujo deslocamento em relação ao corpo focado caia dentro de um cone de 45° em torno do eixo da seta (componente ao longo do eixo `> 0` e `≥` componente perpendicular) MUST ser candidatos; entre eles, o de **menor distância euclidiana** MUST vencer. Se nenhum corpo existir naquela direção, o foco MUST permanecer no corpo atual. Ao trocar, o `focusSize` MUST ser recomputado para o novo corpo (reenquadrando corpo + órbita) e o anel/nome do HUD MUST passar a refletir o novo corpo. Em modo livre (`focused == null`), as setas MUST continuar fazendo pan da câmera, sem trocar foco.

#### Scenario: Pressing an arrow focuses the nearest body in that direction

- **GIVEN** um corpo está focado e existe outro corpo à direita dele
- **WHEN** o usuário pressiona a seta para a direita
- **THEN** `focused` passa a referenciar o corpo mais próximo dentro do cone à direita
- **AND** o `focusSize` é recomputado para o novo corpo

#### Scenario: Arrow with no body in that direction keeps the focus

- **GIVEN** um corpo está focado e não há nenhum corpo dentro do cone naquela direção
- **WHEN** o usuário pressiona aquela seta
- **THEN** `focused` permanece no mesmo corpo

#### Scenario: Arrows still pan when nothing is focused

- **GIVEN** nenhum corpo está focado
- **WHEN** o usuário pressiona as setas
- **THEN** a câmera faz pan como antes (nenhuma troca de foco)
